package ssh_agent

import (
	"bytes"
	"encoding/base64"
	"encoding/binary"
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
	conn, err := a.getConn()
	if err != nil {
		return nil, err
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
	var entries []identityEntry
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

		entries = append(entries, identityEntry{key: pk, alias: item.Alias})
	}

	a.mu.Lock()
	a.identities = entries
	a.mu.Unlock()

	fmt.Fprintf(os.Stderr,
		"[ssh-agent] List(): %d key(s) returned from phone\n",
		len(keys),
	)

	return keys, nil
}

// toMPInt converts raw big-endian bytes to SSH mpint encoding.
// SSH mpint is defined in RFC 4251, Section 5:
//   - 4-byte big-endian length prefix
//   - value bytes (big-endian two's complement)
//   - If the high bit is set, a leading 0x00 byte is prepended
//     so the value is interpreted as positive.
func toMPInt(data []byte) []byte {
	// Trim leading zeros to get the minimal representation.
	trimmed := bytes.TrimLeft(data, "\x00")
	// If the high bit of the first byte is set, prepend 0x00 so the
	// SSH mpint decoder interprets the value as positive.
	if len(trimmed) > 0 && trimmed[0]&0x80 != 0 {
		trimmed = append([]byte{0x00}, trimmed...)
	}
	// 4-byte big-endian length prefix.
	var header [4]byte
	binary.BigEndian.PutUint32(header[:], uint32(len(trimmed)))
	return append(header[:], trimmed...)
}

// ecdsaCurveByteSize returns the byte length of r and s for the given
// ECDSA key type string.
func ecdsaCurveByteSize(keyType string) int {
	switch keyType {
	case ssh.KeyAlgoECDSA256:
		return 32 // P-256
	case ssh.KeyAlgoECDSA384:
		return 48 // P-384
	case ssh.KeyAlgoECDSA521:
		return 66 // P-521
	default:
		return 0
	}
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

	curveByteSize := ecdsaCurveByteSize(key.Type())
	if curveByteSize == 0 {
		return nil, fmt.Errorf("unsupported key type for signing: %s", key.Type())
	}

	if len(response) != curveByteSize*2 {
		return nil, fmt.Errorf(
			"unexpected raw signature length %d for curve size %d (key type %s)",
			len(response), curveByteSize, key.Type(),
		)
	}

	rBytes := response[:curveByteSize]
	sBytes := response[curveByteSize:]

	blob := append(toMPInt(rBytes), toMPInt(sBytes)...)

	fmt.Fprintf(os.Stderr,
		"[ssh-agent] Sign(%s): curve=%d, rawSigLen=%d, blobLen=%d\n",
		key.Type(), curveByteSize, len(response), len(blob),
	)

	return &ssh.Signature{
		Format: key.Type(),
		Blob:   blob,
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
