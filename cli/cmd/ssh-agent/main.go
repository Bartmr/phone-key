package main

import (
	"fmt"
	"os"
	"os/exec"
	"strings"

	ssh_agent "phone-key-cli/internal/cmd/ssh-agent"
)

const (
	phoneServerPort = "8765"
	adbForwardSpec  = "tcp:" + phoneServerPort
)

func main() {
	if err := ensureAdbForward(); err != nil {
		fmt.Fprintf(os.Stderr, "Failed to set up adb forward: %v\n", err)
		os.Exit(1)
	}

	ssh_agent.Main()
}

// ensureAdbForward makes the phone's server reachable on localhost, forwarding
// the local port to the same port on the device if it is not already forwarded.
func ensureAdbForward() error {
	out, err := exec.Command("adb", "forward", "--list").Output()
	if err != nil {
		return fmt.Errorf("failed to list adb forwards: %w", err)
	}

	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		fields := strings.Fields(line)
		if len(fields) >= 2 && fields[1] == adbForwardSpec {
			return nil
		}
	}

	if out, err := exec.Command("adb", "forward", adbForwardSpec, adbForwardSpec).CombinedOutput(); err != nil {
		return fmt.Errorf("failed to run adb forward: %w: %s", err, strings.TrimSpace(string(out)))
	}

	return nil
}
