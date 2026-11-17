package ssh_agent

import (
	"fmt"
	"net"
	"os"
	"os/exec"
	"strings"

	"phone-key-cli/internal/config"

	sshagent "golang.org/x/crypto/ssh/agent"
)

const socketPath = "/tmp/phone-key-ssh-agent.sock"

func Main() {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error loading config: %v\n", err)
		os.Exit(1)
	}

	if err := ensureAdbForward(cfg.Port); err != nil {
		fmt.Fprintf(os.Stderr, "Failed to set up adb forward: %v\n", err)
		os.Exit(1)
	}

	port := cfg.Port
	phoneKeyAgent := NewAgent(port)

	// Remove stale socket file if it exists.
	_ = os.Remove(socketPath)

	listener, err := net.Listen("unix", socketPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to bind to %s: %v\n", socketPath, err)
		os.Exit(1)
	}

	fmt.Fprintf(os.Stderr, "SSH agent listening on %s\n", socketPath)
	fmt.Fprintf(os.Stderr, "Phone server port: %d\n", port)
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

// ensureAdbForward makes the phone's server reachable on localhost, forwarding
// the local port to the same port on the device if it is not already forwarded.
func ensureAdbForward(port int) error {
	forwardSpec := fmt.Sprintf("tcp:%d", port)

	out, err := exec.Command("adb", "forward", "--list").Output()
	if err != nil {
		return fmt.Errorf("failed to list adb forwards: %w", err)
	}

	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		fields := strings.Fields(line)
		if len(fields) >= 2 && fields[1] == forwardSpec {
			return nil
		}
	}

	if out, err := exec.Command("adb", "forward", forwardSpec, forwardSpec).CombinedOutput(); err != nil {
		return fmt.Errorf("failed to run adb forward: %w: %s", err, strings.TrimSpace(string(out)))
	}

	return nil
}
