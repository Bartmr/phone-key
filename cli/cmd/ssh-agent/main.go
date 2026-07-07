package main

import (
	"fmt"
	"net"
	"os"

	sshagent "golang.org/x/crypto/ssh/agent"

	"phone-key-cli/config"
)

const socketPath = "/tmp/phone-key-ssh-agent.sock"

func main() {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error loading config: %v\n", err)
		os.Exit(1)
	}

	if cfg.DeviceAddress == "" {
		fmt.Fprintln(os.Stderr, `"deviceAddress" not set in ~/.phone-key.json`)
		os.Exit(1)
	}

	phoneKeyAgent := NewAgent(cfg.DeviceAddress)

	// Remove stale socket file if it exists.
	_ = os.Remove(socketPath)

	listener, err := net.Listen("unix", socketPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to bind to %s: %v\n", socketPath, err)
		os.Exit(1)
	}

	fmt.Fprintf(os.Stderr, "SSH agent listening on %s\n", socketPath)
	fmt.Fprintf(os.Stderr, "export SSH_AUTH_SOCK=%s\n", socketPath)

	for {
		conn, err := listener.Accept()
		if err != nil {
			fmt.Fprintf(os.Stderr, "Accept error: %v\n", err)
			continue
		}
		go func() {
			if err := sshagent.ServeAgent(phoneKeyAgent, conn); err != nil {
				fmt.Fprintf(os.Stderr, "Agent serve error: %v\n", err)
			}
		}()
	}
}
