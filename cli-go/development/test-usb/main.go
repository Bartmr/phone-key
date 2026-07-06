package main

import (
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"phone-key-cli/src/phonekey"
)

func main() {
	conn, err := phonekey.ConnectUsb()
	if err != nil {
		fmt.Fprintf(os.Stderr, "failed to connect: %v\n", err)
		os.Exit(1)
	}
	defer conn.Close()

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sigCh
		fmt.Fprintln(os.Stderr, "\ndisconnecting...")
		conn.Close()
		os.Exit(0)
	}()

	payload := []byte(`{"type":"echo","payload":"Hello from Phone Key CLI"}`)
	fmt.Printf("sending %d bytes over USB...\n", len(payload))

	response, err := conn.SendMessage(payload)
	if err != nil {
		fmt.Fprintf(os.Stderr, "send failed: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("received %d bytes: %s\n", len(response), string(response))
}
