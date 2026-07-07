package main

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"phone-key-cli/bluetooth"
	"phone-key-cli/config"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error loading config: %v\n", err)
		os.Exit(1)
	}

	deviceAddress := cfg.DeviceAddress
	if deviceAddress == "" {
		fmt.Fprintln(os.Stderr, `"deviceAddress" not set in ~/.phone-key.json`)
		os.Exit(1)
	}

	conn, err := bluetooth.Connect(deviceAddress)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Bluetooth connection failed: %v\n", err)
		os.Exit(1)
	}

	largePayload := strings.Repeat("X", 2000)

	messageJSON, err := json.Marshal(map[string]string{
		"type": "echo",
		"data": largePayload,
	})
	if err != nil {
		fmt.Fprintf(os.Stderr, "Failed to marshal message: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("Sending %d bytes over BLE...\n", len(messageJSON))

	// Create a context that cancels on SIGINT.
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT)
	defer cancel()

	type result struct {
		data []byte
		err  error
	}

	resultCh := make(chan result, 1)
	go func() {
		data, err := conn.SendMessage(string(messageJSON))
		resultCh <- result{data, err}
	}()

	select {
	case r := <-resultCh:
		if r.err != nil {
			fmt.Fprintf(os.Stderr, "Error sending message: %v\n", r.err)
			_ = conn.Disconnect()
			os.Exit(1)
		}
		fmt.Printf("Received %d bytes: %s\n", len(r.data), string(r.data))
	case <-ctx.Done():
		fmt.Fprintln(os.Stderr, "\nCtrl+C received, disconnecting...")
		_ = conn.Disconnect()
		fmt.Println("Disconnected.")
		return
	}

	if err := conn.Disconnect(); err != nil {
		fmt.Fprintf(os.Stderr, "Disconnect error: %v\n", err)
	}
	fmt.Println("Disconnected.")
}
