package phonekey

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"os/signal"
	"sync"
	"syscall"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/agent"
)

const SocketPath = "/tmp/phone-key-ssh-agent.sock"

type identityResponse struct {
	Alias           string `json:"alias"`
	PublicKeyBase64 string `json:"publicKeyBase64"`
}

type requestIdentities struct {
	Type string `json:"type"`
}

type signRequest struct {
	Type     string `json:"type"`
	KeyAlias string `json:"keyAlias"`
	Data     string `json:"data"`
}

type AppAgent struct {
	mu         sync.Mutex
	conn       *UsbConnection
	identities []identityEntry
}

type identityEntry struct {
	key     *agent.Key
	cred    ssh.PublicKey
	comment string
}

func NewAppAgent() *AppAgent {
	return &AppAgent{}
}

func (a *AppAgent) ensureConnection() error {
	if a.conn != nil {
		return nil
	}
	conn, err := ConnectUsb()
	if err != nil {
		return err
	}
	a.conn = conn
	return nil
}

func (a *AppAgent) Disconnect() {
	if a.conn != nil {
		a.conn.Close()
		a.conn = nil
	}
}

func (a *AppAgent) sendAndReceive(msg interface{}) ([]byte, error) {
	payload, err := json.Marshal(msg)
	if err != nil {
		return nil, fmt.Errorf("marshal: %w", err)
	}
	response, err := a.conn.SendMessage(payload)
	if err != nil {
		a.Disconnect()
		return nil, err
	}
	return response, nil
}

func (a *AppAgent) List() ([]*agent.Key, error) {
	a.mu.Lock()
	defer a.mu.Unlock()

	if err := a.ensureConnection(); err != nil {
		return nil, err
	}

	resp, err := a.sendAndReceive(requestIdentities{Type: "ssh-request-identities"})
	if err != nil {
		return nil, err
	}

	var parsed []identityResponse
	if err := json.Unmarshal(resp, &parsed); err != nil {
		return nil, fmt.Errorf("parse identities: %w", err)
	}

	a.identities = nil
	var keys []*agent.Key
	for _, item := range parsed {
		pubKeyBytes, err := base64.StdEncoding.DecodeString(item.PublicKeyBase64)
		if err != nil {
			fmt.Fprintf(os.Stderr, "[ssh-agent] base64 decode failed for '%s': %v\n", item.Alias, err)
			continue
		}

		pubKey, _, _, _, err := ssh.ParseAuthorizedKey(pubKeyBytes)
		if err != nil {
			fmt.Fprintf(os.Stderr, "[ssh-agent] parse public key failed for '%s': %v\n", item.Alias, err)
			continue
		}

		key := &agent.Key{
			Format:  pubKey.Type(),
			Blob:    pubKey.Marshal(),
			Comment: item.Alias,
		}
		keys = append(keys, key)
		a.identities = append(a.identities, identityEntry{
			key:     key,
			cred:    pubKey,
			comment: item.Alias,
		})
	}

	return keys, nil
}

func (a *AppAgent) Sign(key ssh.PublicKey, data []byte) (*ssh.Signature, error) {
	a.mu.Lock()
	defer a.mu.Unlock()

	var entry *identityEntry
	for i := range a.identities {
		if a.identities[i].cred.Type() == key.Type() &&
			string(a.identities[i].cred.Marshal()) == string(key.Marshal()) {
			entry = &a.identities[i]
			break
		}
	}
	if entry == nil {
		return nil, fmt.Errorf("key not found in cached identities")
	}

	if err := a.ensureConnection(); err != nil {
		return nil, err
	}

	dataB64 := base64.StdEncoding.EncodeToString(data)
	resp, err := a.sendAndReceive(signRequest{
		Type:     "ssh-sign",
		KeyAlias: entry.comment,
		Data:     dataB64,
	})
	if err != nil {
		return nil, err
	}

	if len(resp) == 0 {
		return nil, fmt.Errorf("signing failed on device")
	}

	sig := &ssh.Signature{
		Format: key.Type(),
		Blob:   resp,
	}
	return sig, nil
}

func (a *AppAgent) Signers() ([]ssh.Signer, error) {
	return nil, nil
}

func (a *AppAgent) Add(key agent.AddedKey) error {
	return fmt.Errorf("adding keys is not supported")
}

func (a *AppAgent) Remove(key ssh.PublicKey) error {
	return fmt.Errorf("removing keys is not supported")
}

func (a *AppAgent) RemoveAll() error {
	return fmt.Errorf("removing keys is not supported")
}

func (a *AppAgent) Lock(passphrase []byte) error {
	return fmt.Errorf("locking is not supported")
}

func (a *AppAgent) Unlock(passphrase []byte) error {
	return fmt.Errorf("unlocking is not supported")
}

func RunSSHAgent() error {
	_ = os.Remove(SocketPath)

	listener, err := net.Listen("unix", SocketPath)
	if err != nil {
		return fmt.Errorf("listen on %s: %w", SocketPath, err)
	}
	defer os.Remove(SocketPath)

	fmt.Printf("SSH agent listening on %s\n", SocketPath)
	fmt.Printf("export SSH_AUTH_SOCK=%s\n", SocketPath)

	app := NewAppAgent()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		fmt.Fprintln(os.Stderr, "\n[ssh-agent] shutting down...")
		app.Disconnect()
		listener.Close()
		os.Remove(SocketPath)
		os.Exit(0)
	}()

	for {
		conn, err := listener.Accept()
		if err != nil {
			select {
			case <-sigCh:
				return nil
			default:
				return fmt.Errorf("accept: %w", err)
			}
		}
		go func() {
			if err := agent.ServeAgent(app, conn); err != nil {
				fmt.Fprintf(os.Stderr, "[ssh-agent] session error: %v\n", err)
			}
		}()
	}
}
