package ssh_agent

import (
	"bytes"
	"crypto/x509"
	"encoding/asn1"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/agent"
)

// listKeysResult is the JSON wrapper for a list-keys result.
type listKeysResult struct {
	Keys []keyEntryResult `json:"keys"`
}

// keyEntryResult matches the JSON the phone sends for each key.
type keyEntryResult struct {
	Alias                              string   `json:"alias"`
	Algorithm                          string   `json:"algorithm"`
	KeySize                            int      `json:"keySize"`
	Purposes                           int      `json:"purposes"`
	Digests                            []string `json:"digests"`
	SignaturePaddings                  []string `json:"signaturePaddings"`
	EncryptionPaddings                 []string `json:"encryptionPaddings"`
	BlockModes                         []string `json:"blockModes"`
	UserAuthenticationRequired         bool     `json:"userAuthenticationRequired"`
	UserAuthenticationValidityDuration int      `json:"userAuthenticationValidityDurationSeconds"`
	PublicKeyBase64                    *string  `json:"publicKeyBase64"`
}

// Android KeyProperties.PURPOSE_SIGN = 4
const purposeSign = 4

// signResult is the JSON result for a successful sign command.
type signResult struct {
	Signature string `json:"signature"`
}

// PhoneKeyAgent implements the golang.org/x/crypto/ssh/agent.Agent interface
// by proxying requests to the phone over the local network.
type PhoneKeyAgent struct {
	mu         sync.Mutex
	identities []identityEntry
	serverURL  string
}

type identityEntry struct {
	key       ssh.PublicKey
	alias     string
	algorithm string // Java Signature algorithm string (e.g. "SHA256withECDSA")
}

// NewAgent creates a PhoneKeyAgent configured for the given local port.
func NewAgent(port int) *PhoneKeyAgent {
	return &PhoneKeyAgent{
		serverURL: fmt.Sprintf("http://127.0.0.1:%d", port),
	}
}

// request sends a JSON payload to the given phone endpoint and returns the
// response status code and body. Callers are responsible for checking the
// status code.
func (a *PhoneKeyAgent) request(path string, method string, payload any) (int, []byte, error) {
	var body io.Reader
	if payload != nil {
		data, err := json.Marshal(payload)
		if err != nil {
			return 0, nil, fmt.Errorf("failed to marshal request: %w", err)
		}
		body = bytes.NewReader(data)
	}

	req, err := http.NewRequest(method, a.serverURL+path, body)
	if err != nil {
		return 0, nil, fmt.Errorf("failed to create request: %w", err)
	}
	if payload != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	client := &http.Client{Timeout: 2 * time.Minute}
	resp, err := client.Do(req)
	if err != nil {
		return 0, nil, fmt.Errorf("request to phone failed: %w", err)
	}
	defer resp.Body.Close()

	responseBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return 0, nil, fmt.Errorf("failed to read response: %w", err)
	}

	return resp.StatusCode, responseBody, nil
}

// List sends the list-keys command to the phone and returns the
// parsed key list.
func (a *PhoneKeyAgent) List() ([]*agent.Key, error) {
	status, responseBody, err := a.request("/list-keys", http.MethodPost, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to list keys: %w", err)
	}
	if status < http.StatusOK || status >= http.StatusMultipleChoices {
		return nil, fmt.Errorf(
			"phone returned HTTP %d: %s",
			status,
			strings.TrimSpace(string(responseBody)),
		)
	}

	fmt.Fprintf(os.Stderr, "[ssh-agent] raw list-keys response: %s\n", string(responseBody))

	var parsed listKeysResult
	if err := json.Unmarshal(responseBody, &parsed); err != nil {
		return nil, fmt.Errorf("failed to decode list-keys response: %w", err)
	}

	return a.keysFromEntries(parsed.Keys)
}

// keysFromEntries converts key entry responses from the phone into SSH agent keys,
// caching the identities for later Sign calls.
func (a *PhoneKeyAgent) keysFromEntries(items []keyEntryResult) ([]*agent.Key, error) {

	a.mu.Lock()
	a.identities = nil
	a.mu.Unlock()

	var keys []*agent.Key
	var entries []identityEntry
	for _, item := range items {
		// Skip keys without a public key (symmetric keys like AES, HMAC).
		if item.PublicKeyBase64 == nil {
			fmt.Fprintf(os.Stderr,
				"[ssh-agent] List(): skipping '%s' — no public key (symmetric key: %s)\n",
				item.Alias, item.Algorithm,
			)
			continue
		}

		// Skip keys that don't have the SIGN purpose.
		if item.Purposes&purposeSign == 0 {
			fmt.Fprintf(os.Stderr,
				"[ssh-agent] List(): skipping '%s' — missing SIGN purpose (purposes=%d)\n",
				item.Alias, item.Purposes,
			)
			continue
		}

		publicKeyDER, err := base64.StdEncoding.DecodeString(*item.PublicKeyBase64)
		if err != nil {
			return nil, fmt.Errorf(
				"failed to base64-decode public key for '%s': %w",
				item.Alias, err,
			)
		}

		cryptoPubKey, err := x509.ParsePKIXPublicKey(publicKeyDER)
		if err != nil {
			return nil, fmt.Errorf(
				"failed to parse public key for '%s': %w",
				item.Alias, err,
			)
		}

		pk, err := ssh.NewPublicKey(cryptoPubKey)
		if err != nil {
			return nil, fmt.Errorf(
				"failed to parse public key for '%s': %w",
				item.Alias, err,
			)
		}

		algorithm := deriveAlgorithm(pk.Type(), item.Digests)

		fmt.Fprintln(os.Stderr, ssh.MarshalAuthorizedKey(pk))

		keys = append(keys, &agent.Key{
			Format:  pk.Type(),
			Blob:    pk.Marshal(),
			Comment: item.Alias,
		})

		entries = append(entries, identityEntry{key: pk, alias: item.Alias, algorithm: algorithm})
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

// deriveAlgorithm returns the Java Signature algorithm string for the given
// SSH key type and configured digests.
func deriveAlgorithm(keyType string, digests []string) string {
	digest := "SHA256"
	if len(digests) > 0 {
		digest = digests[0]
	}
	digestSuffix := strings.ReplaceAll(digest, "-", "")

	switch keyType {
	case ssh.KeyAlgoECDSA256, ssh.KeyAlgoECDSA384, ssh.KeyAlgoECDSA521:
		return digestSuffix + "withECDSA"
	case ssh.KeyAlgoRSA:
		return digestSuffix + "withRSA"
	default:
		return digestSuffix + "withECDSA"
	}
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

// ecdsaSig is used for ASN.1 DER parsing of ECDSA signatures.
type ecdsaSig struct {
	R, S *big.Int
}

// Sign sends a signing request to the phone for the given key and data.
func (a *PhoneKeyAgent) Sign(key ssh.PublicKey, data []byte) (*ssh.Signature, error) {
	a.mu.Lock()
	var alias string
	var algorithm string
	for _, entry := range a.identities {
		if string(entry.key.Marshal()) == string(key.Marshal()) {
			alias = entry.alias
			algorithm = entry.algorithm
			break
		}
	}
	a.mu.Unlock()

	if alias == "" {
		return nil, fmt.Errorf("key not found in cached identities")
	}

	dataB64 := base64.StdEncoding.EncodeToString(data)

	status, responseBody, err := a.request("/sign", http.MethodPost, map[string]string{
		"keyAlias":  alias,
		"data":      dataB64,
		"algorithm": algorithm,
	})
	if err != nil {
		return nil, fmt.Errorf("sign request failed: %w", err)
	}
	if status < http.StatusOK || status >= http.StatusMultipleChoices {
		return nil, fmt.Errorf(
			"phone returned HTTP %d: %s",
			status,
			strings.TrimSpace(string(responseBody)),
		)
	}

	var signResp signResult
	if err := json.Unmarshal(responseBody, &signResp); err != nil {
		return nil, fmt.Errorf("failed to decode sign response: %w", err)
	}

	return a.buildSignature(key, signResp.Signature)
}

// buildSignature decodes a base64-encoded signature from the phone and converts
// it into an SSH signature blob.
func (a *PhoneKeyAgent) buildSignature(key ssh.PublicKey, signatureB64 string) (*ssh.Signature, error) {
	signatureBytes, err := base64.StdEncoding.DecodeString(signatureB64)
	if err != nil {
		return nil, fmt.Errorf("failed to decode signature base64: %w", err)
	}

	keyType := key.Type()
	var blob []byte

	switch keyType {
	case ssh.KeyAlgoECDSA256, ssh.KeyAlgoECDSA384, ssh.KeyAlgoECDSA521:
		var sig ecdsaSig
		_, err := asn1.Unmarshal(signatureBytes, &sig)
		if err != nil {
			return nil, fmt.Errorf("failed to parse DER signature: %w", err)
		}

		curveByteSize := ecdsaCurveByteSize(keyType)
		if curveByteSize == 0 {
			return nil, fmt.Errorf("unsupported key type for signing: %s", keyType)
		}

		// Convert *big.Int r and s to fixed-length big-endian bytes.
		rBytes := sig.R.Bytes()
		rPadded := make([]byte, curveByteSize)
		copyStartR := curveByteSize - len(rBytes)
		if copyStartR < 0 {
			rBytes = rBytes[len(rBytes)-curveByteSize:]
			copyStartR = 0
		}
		copy(rPadded[copyStartR:], rBytes)

		sBytes := sig.S.Bytes()
		sPadded := make([]byte, curveByteSize)
		copyStartS := curveByteSize - len(sBytes)
		if copyStartS < 0 {
			sBytes = sBytes[len(sBytes)-curveByteSize:]
			copyStartS = 0
		}
		copy(sPadded[copyStartS:], sBytes)

		blob = append(toMPInt(rPadded), toMPInt(sPadded)...)

		fmt.Fprintf(os.Stderr,
			"[ssh-agent] Sign(%s): curve=%d, blobLen=%d\n",
			keyType, curveByteSize, len(blob),
		)

	case ssh.KeyAlgoRSA:
		// RSA signatures from Signature.sign() are already PKCS#1 v1.5 format,
		// which is what SSH expects. Wrap with mpint encoding.
		blob = toMPInt(signatureBytes)

	default:
		return nil, fmt.Errorf("unsupported key type for signing: %s", keyType)
	}

	return &ssh.Signature{
		Format: keyType,
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
