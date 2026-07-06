package main

import (
	"fmt"
	"os"

	"phone-key-cli/src/phonekey"
)

func main() {
	if err := phonekey.RunSSHAgent(); err != nil {
		fmt.Fprintf(os.Stderr, "fatal: %v\n", err)
		os.Exit(1)
	}
}
