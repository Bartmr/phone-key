package ssh_agent

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"sync"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/agent"

	"phone-key-cli/internal/core/bluetooth"
)

// identityResponse matches the JSON the phone sends for each identity.
type identityResponse struct {
	Alias           string `json:"alias"`
	PublicKeyBase64 string `json:"publicKeyBase64"`
}

// PhoneKeyAgent implements the golang.org/x/crypto/ssh/agent.Agent interface
// by proxying requests to the phone over Bluetooth.
type PhoneKeyAgent struct {
	deviceAddress string
	mu            sync.Mutex
	conn          *bluetooth.Connection
	identities    []identityEntry
}

type identityEntry struct {
	key   ssh.PublicKey
	alias string
}

// NewAgent creates a PhoneKeyAgent configured for the given device address.
func NewAgent(deviceAddress string) *PhoneKeyAgent {
	return &PhoneKeyAgent{deviceAddress: deviceAddress}
}

// getConn lazily initializes and returns the Bluetooth connection.
// If the connection is broken, it is discarded and recreated on the next call.
func (a *PhoneKeyAgent) getConn() (*bluetooth.Connection, error) {
	a.mu.Lock()
	defer a.mu.Unlock()

	if a.conn != nil {
		return a.conn, nil
	}

	conn, err := bluetooth.Connect(a.deviceAddress)
	if err != nil {
		return nil, err
	}
	a.conn = conn
	return a.conn, nil
}

// sendMessage sends a JSON message over Bluetooth and returns the raw response.
// On error, the cached connection is discarded so the next call creates a fresh one.
func (a *PhoneKeyAgent) sendMessage(jsonStr string) ([]byte, error) {
	a.mu.Lock()
	conn := a.conn
	a.mu.Unlock()

	if conn == nil {
		var err error
		conn, err = a.getConn()
		if err != nil {
			return nil, err
		}
	}

	response, err := conn.SendMessage(jsonStr)
	if err != nil {
		// Discard broken connection so the next call creates a fresh one.
		a.mu.Lock()
		a.conn = nil
		a.mu.Unlock()
		fmt.Fprintf(os.Stderr, "[ssh-agent] Bluetooth connection error: %v\n", err)
		return nil, err
	}
	return response, nil
}

// List sends the ssh-request-identities command to the phone and returns the
// parsed key list.
func (a *PhoneKeyAgent) List() ([]*agent.Key, error) {
	response, err := a.sendMessage(`{"type":"ssh-request-identities"}`)
	if err != nil {
		return nil, fmt.Errorf("failed to request identities: %w", err)
	}

	var parsed []identityResponse
	if err := json.Unmarshal(response, &parsed); err != nil {
		return nil, fmt.Errorf("failed to parse identities response: %w", err)
	}

	a.mu.Lock()
	a.identities = nil
	a.mu.Unlock()

	var keys []*agent.Key
	for _, item := range parsed {
		publicKeyBytes, err := base64.StdEncoding.DecodeString(item.PublicKeyBase64)
		if err != nil {
			return nil, fmt.Errorf(
				"failed to base64-decode public key for '%s': %w",
				item.Alias, err,
			)
		}

		pk, _, _, _, err := ssh.ParseAuthorizedKey(publicKeyBytes)
		if err != nil {
			return nil, fmt.Errorf(
				"failed to parse public key for '%s': %w",
				item.Alias, err,
			)
		}

		fmt.Fprintln(os.Stderr, string(publicKeyBytes))

		keys = append(keys, &agent.Key{
			Format:  pk.Type(),
			Blob:    pk.Marshal(),
			Comment: item.Alias,
		})

		a.mu.Lock()
		a.identities = append(a.identities, identityEntry{key: pk, alias: item.Alias})
		a.mu.Unlock()
	}

	return keys, nil
}

// Sign sends a signing request to the phone for the given key and data.
func (a *PhoneKeyAgent) Sign(key ssh.PublicKey, data []byte) (*ssh.Signature, error) {
	a.mu.Lock()
	var alias string
	for _, entry := range a.identities {
		if string(entry.key.Marshal()) == string(key.Marshal()) {
			alias = entry.alias
			break
		}
	}
	a.mu.Unlock()

	if alias == "" {
		return nil, fmt.Errorf("key not found in cached identities")
	}

	dataB64 := base64.StdEncoding.EncodeToString(data)

	requestJSON, err := json.Marshal(map[string]string{
		"type":     "ssh-sign",
		"keyAlias": alias,
		"data":     dataB64,
	})
	if err != nil {
		return nil, fmt.Errorf("failed to marshal sign request: %w", err)
	}

	response, err := a.sendMessage(string(requestJSON))
	if err != nil {
		return nil, fmt.Errorf("sign request failed: %w", err)
	}

	if len(response) == 0 {
		return nil, fmt.Errorf("signing failed on device: empty response")
	}

	return &ssh.Signature{
		Format: key.Type(),
		Blob:   response,
	}, nil
}

// Add is a no-op — the phone manages its own keys.
func (a *PhoneKeyAgent) Add(key agent.AddedKey) error {
	return nil
}

// Remove is a no-op — the phone manages its own keys.
func (a *PhoneKeyAgent) Remove(key ssh.PublicKey) error {
	return nil
}

// RemoveAll is a no-op — the phone manages its own keys.
func (a *PhoneKeyAgent) RemoveAll() error {
	return nil
}

// Lock is a no-op — key access is controlled on the phone.
func (a *PhoneKeyAgent) Lock(passphrase []byte) error {
	return nil
}

// Unlock is a no-op — key access is controlled on the phone.
func (a *PhoneKeyAgent) Unlock(passphrase []byte) error {
	return nil
}

// Signers returns an empty list. Signing is handled by the Sign method directly.
func (a *PhoneKeyAgent) Signers() ([]ssh.Signer, error) {
	return nil, nil
}

// Ensure PhoneKeyAgent implements agent.Agent.
var _ agent.Agent = (*PhoneKeyAgent)(nil)
