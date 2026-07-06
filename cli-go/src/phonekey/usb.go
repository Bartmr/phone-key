package phonekey

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"os"
	"time"

	"github.com/google/gousb"
)

// AOA protocol constants (Android Open Accessory 2.0).
const (
	aoaProtocolVersion = 2

	// Control request types.
	usbTypeVendor = 0x40 // device-to-host, vendor
	usbTypeIn     = 0xC0 // host-to-device, vendor

	// AOA control requests.
	aoaGetProtocol    = 51
	aoaSendString     = 52
	aoaStartAccessory = 53

	// AOA device identifiers after re-enumeration.
	aoaVID        = 0x18D1
	aoaPID        = 0x2D00
	aoaPIDWithADB = 0x2D01

	// Bulk endpoint addresses.
	aoaEndpointOut = 0x02
	aoaEndpointIn  = 0x81
)

var (
	ErrDeviceNotFound   = errors.New("no AOA-compatible Android device found")
	ErrAOAUnsupported   = errors.New("device does not support Android Open Accessory protocol")
	ErrUSBCommunication = errors.New("USB communication error")
)

type UsbConnection struct {
	ctx   *gousb.Context
	dev   *gousb.Device
	intf  *gousb.Interface
	inEP  *gousb.InEndpoint
	outEP *gousb.OutEndpoint
}

func ConnectUsb() (*UsbConnection, error) {
	ctx := gousb.NewContext()

	dev, err := findAndActivateAOA(ctx)
	if err != nil {
		ctx.Close()
		return nil, err
	}

	conn, err := openAOADevice(ctx, dev)
	if err != nil {
		dev.Close()
		ctx.Close()
		return nil, err
	}
	return conn, nil
}

func findAndActivateAOA(ctx *gousb.Context) (*gousb.Device, error) {
	devs, err := ctx.OpenDevices(func(desc *gousb.DeviceDesc) bool {
		return desc.Vendor == 0x22d9 && desc.Product == 0x2769
	})
	if err != nil {
		return nil, fmt.Errorf("list USB devices: %w", err)
	}
	defer func() {
		for _, d := range devs {
			d.Close()
		}
	}()

	for _, dev := range devs {
		if isAOACompatible(dev) {
			if err := activateAOA(dev); err != nil {
				fmt.Fprintf(os.Stderr, "[usb] AOA activation failed for device %s: %v\n", dev.String(), err)
				continue
			}
			dev.Close()
			return waitForAOAReenumeration(ctx)
		}
	}
	return nil, ErrDeviceNotFound
}

func isAOACompatible(dev *gousb.Device) bool {
	buf := make([]byte, 2)
	n, err := dev.Control(
		usbTypeIn,
		aoaGetProtocol,
		0,
		0,
		buf,
	)
	if err != nil || n < 2 {
		return false
	}
	version := binary.LittleEndian.Uint16(buf)
	return version >= 1
}

func activateAOA(dev *gousb.Device) error {
	strings := []string{
		"PhoneKey",
		"PhoneKey CLI",
		"Phone Key USB",
		"1.0",
		"https://phonekey.dev",
		"phone-key-001",
	}

	for i, s := range strings {
		if err := sendControlString(dev, aoaSendString, uint16(i), s); err != nil {
			return fmt.Errorf("send string %d: %w", i, err)
		}
	}

	_, err := dev.Control(usbTypeVendor, aoaStartAccessory, 0, 0, nil)
	if err != nil {
		return fmt.Errorf("send start: %w", err)
	}

	fmt.Fprintln(os.Stderr, "[usb] AOA start sent, waiting for device to re-enumerate...")
	return nil
}

func sendControlString(dev *gousb.Device, request uint8, index uint16, value string) error {
	payload := []byte(value + "\x00")
	_, err := dev.Control(usbTypeVendor, request, 0, index, payload)
	return err
}

func waitForAOAReenumeration(ctx *gousb.Context) (*gousb.Device, error) {
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		devs, err := ctx.OpenDevices(func(desc *gousb.DeviceDesc) bool {
			return desc.Vendor == aoaVID &&
				(desc.Product == aoaPID || desc.Product == aoaPIDWithADB)
		})
		if err != nil {
			time.Sleep(200 * time.Millisecond)
			continue
		}
		if len(devs) > 0 {
			for i := 1; i < len(devs); i++ {
				devs[i].Close()
			}
			fmt.Fprintln(os.Stderr, "[usb] AOA device re-enumerated successfully")
			return devs[0], nil
		}
		time.Sleep(200 * time.Millisecond)
	}
	return nil, fmt.Errorf("%w: device did not re-enumerate in AOA mode within 10 s", ErrDeviceNotFound)
}

func openAOADevice(ctx *gousb.Context, dev *gousb.Device) (*UsbConnection, error) {
	dev.SetAutoDetach(true)

	intf, done, err := dev.DefaultInterface()
	if err != nil {
		dev.Close()
		return nil, fmt.Errorf("claim default interface: %w", err)
	}
	_ = done

	inEP, err := intf.InEndpoint(aoaEndpointIn)
	if err != nil {
		intf.Close()
		dev.Close()
		return nil, fmt.Errorf("open IN endpoint: %w", err)
	}

	outEP, err := intf.OutEndpoint(aoaEndpointOut)
	if err != nil {
		intf.Close()
		dev.Close()
		return nil, fmt.Errorf("open OUT endpoint: %w", err)
	}

	fmt.Fprintln(os.Stderr, "[usb] AOA connection established")

	return &UsbConnection{
		ctx:   ctx,
		dev:   dev,
		intf:  intf,
		inEP:  inEP,
		outEP: outEP,
	}, nil
}

func (c *UsbConnection) SendMessage(payload []byte) ([]byte, error) {
	lenBuf := make([]byte, 4)
	binary.BigEndian.PutUint32(lenBuf, uint32(len(payload)))

	if _, err := c.outEP.Write(append(lenBuf, payload...)); err != nil {
		return nil, fmt.Errorf("%w: write: %v", ErrUSBCommunication, err)
	}

	if _, err := io.ReadFull(c.inEP, lenBuf); err != nil {
		return nil, fmt.Errorf("%w: read length: %v", ErrUSBCommunication, err)
	}
	responseLen := binary.BigEndian.Uint32(lenBuf)

	response := make([]byte, responseLen)
	if _, err := io.ReadFull(c.inEP, response); err != nil {
		return nil, fmt.Errorf("%w: read payload: %v", ErrUSBCommunication, err)
	}

	return response, nil
}

func (c *UsbConnection) Close() error {
	c.intf.Close()
	c.dev.Close()
	c.ctx.Close()
	return nil
}
