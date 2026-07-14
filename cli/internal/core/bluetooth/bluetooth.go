package bluetooth

import (
	"context"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/godbus/dbus/v5"
	"github.com/muka/go-bluetooth/bluez"
	"github.com/muka/go-bluetooth/bluez/profile/adapter"
	"github.com/muka/go-bluetooth/bluez/profile/device"
	"github.com/muka/go-bluetooth/bluez/profile/gatt"
)

const (
	ServiceUUID        = "a667f940-6a50-49ac-9b75-2b9639564972"
	CharacteristicUUID = "69924d24-8e47-4d43-9e86-dde30201a474"
)

type Connection struct {
	device *device.Device1
	char   *gatt.GattCharacteristic1

	propCh      chan *bluez.PropertyChanged
	cancelWatch func()
}

// Connect establishes a BLE connection to the given device address.
func Connect(deviceAddress string) (*Connection, error) {
	dev, err := device.NewDevice(adapter.GetDefaultAdapterID(), deviceAddress)
	if err != nil {
		return nil, fmt.Errorf("failed to create device: %w", err)
	}

	dev_connected, err := dev.GetConnected()

	if !dev_connected {
		err = dev.Connect()
		if err != nil {
			return nil, fmt.Errorf("failed to connect: %w", err)
		}
	}

	// Force service LTE discovery

	dbusConn, err := dbus.SystemBus()
	if err != nil {
		return nil, fmt.Errorf("failed to connect to system D-Bus: %w", err)
	}

	macUnderscore := strings.ReplaceAll(deviceAddress, ":", "_")
	devicePath := dbus.ObjectPath(fmt.Sprintf("/org/bluez/hci0/dev_%s", macUnderscore))
	deviceObj := dbusConn.Object("org.bluez", devicePath)

	call := deviceObj.Call("org.bluez.Device1.Connect", 0)
	if call.Err != nil {
		return nil, fmt.Errorf("org.bluez.Device1.Connect failed: %w", call.Err)
	}

	// ---

	fmt.Fprintln(os.Stderr, "[bluetooth] device connected, waiting for services to resolve...")

	// Wait for ServicesResolved (10 s timeout)
	deadline := time.Now().Add(10 * time.Second)
	for {
		props, err := dev.GetProperties()
		if err == nil && props.ServicesResolved {
			break
		}
		if time.Now().After(deadline) {
			return nil, fmt.Errorf("services not resolved within 10 s after connect")
		}
		time.Sleep(100 * time.Millisecond)
	}

	fmt.Fprintln(os.Stderr, "[bluetooth] services resolved...")

	chars, err := dev.GetCharacteristics()
	if err != nil {
		return nil, fmt.Errorf("failed to get characteristics: %w", err)
	}

	loggedServices := map[string]bool{}
	var targetChar *gatt.GattCharacteristic1

	for _, char := range chars {
		svcPath := char.Properties.Service
		if svc, err := gatt.NewGattService1(svcPath); err == nil {
			svcUUID := strings.ToUpper(svc.Properties.UUID)
			if !loggedServices[svcUUID] {
				loggedServices[svcUUID] = true
				fmt.Fprintf(os.Stderr, "[bluetooth]   service: %s\n", svcUUID)
			}
		}

		charUUID := strings.ToUpper(char.Properties.UUID)
		fmt.Fprintf(os.Stderr, "[bluetooth]     characteristic: %s\n", charUUID)
		if charUUID == strings.ToUpper(CharacteristicUUID) {
			targetChar = char
		}
	}

	if targetChar == nil {
		return nil, fmt.Errorf(
			"characteristic %s not found on device. "+
				"Found %d service(s). "+
				"Make sure the Android app is in the foreground with the GATT server running.",
			CharacteristicUUID,
			len(loggedServices),
		)
	}

	fmt.Fprintln(os.Stderr, "[bluetooth] characteristic found, enabling notifications...")

	err = targetChar.StartNotify()
	if err != nil {
		return nil, fmt.Errorf("StartNotify failed: %w", err)
	}

	propCh, err := targetChar.WatchProperties()
	if err != nil {
		return nil, fmt.Errorf("WatchProperties failed: %w", err)
	}

	fmt.Fprintln(os.Stderr, "[bluetooth] notifications enabled, connection established")

	return &Connection{
		device:      dev,
		char:        targetChar,
		propCh:      propCh,
		cancelWatch: func() { targetChar.UnwatchProperties(propCh) },
	}, nil
}

// SendMessage writes a JSON string to the GATT characteristic and waits for a
// framed response. The response is delimited by a single 0x02 byte (same protocol
// as the Rust implementation). The function returns the accumulated payload
// without the delimiter.
func (c *Connection) SendMessage(jsonStr []byte) ([]byte, error) {
	err := c.char.WriteValue(jsonStr, map[string]interface{}{"type": "reliable"})
	if err != nil {
		return nil, fmt.Errorf("GATT write failed: %w", err)
	}

	// Read response chunks delimited by 0x02, with 60 s timeout.
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()

	var buffer []byte
	for {
		select {
		case prop := <-c.propCh:
			if prop == nil || prop.Name != "Value" {
				continue
			}
			chunk, ok := prop.Value.([]byte)
			if !ok {
				continue
			}
			if len(chunk) == 1 && chunk[0] == 0x02 {
				if len(buffer) == 0 {
					return nil, fmt.Errorf("empty response received from device")
				}
				return buffer, nil
			}
			buffer = append(buffer, chunk...)
		case <-ctx.Done():
			return nil, fmt.Errorf("timed out waiting for response from device")
		}
	}
}

// Disconnect tears down the BLE connection.
func (c *Connection) Disconnect() {
	// UnwatchProperties blocks if nothing is reading from propCh,
	// so drain it in a goroutine to unblock the teardown.
	go func() {
		for range c.propCh {
		}
	}()
	c.cancelWatch()

	if c.char != nil {
		if err := c.char.StopNotify(); err != nil {
			fmt.Fprintf(os.Stderr, "[bluetooth] stop notify error: %v\n", err)
		}
	}
}
