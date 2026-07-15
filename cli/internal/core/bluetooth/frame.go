package bluetooth

import "fmt"

// frameReader accumulates byte chunks delimited by 0x01 (start) and 0x02 (end).
// It is not safe for concurrent use.
type frameReader struct {
	buf []byte
}

// feed processes a chunk and returns a completed payload when the end marker
// (0x02) is received as a single-byte chunk. The returned payload excludes the
// framing bytes.
func (r *frameReader) feed(chunk []byte) ([]byte, error) {

	if len(chunk) == 1 && chunk[0] == 0x01 {
		// Start of message — reset buffer for a fresh read.
		r.buf = r.buf[:0]
		return nil, nil
	}

	if len(chunk) == 1 && chunk[0] == 0x02 {
		if len(r.buf) == 0 {
			return nil, fmt.Errorf("empty response received from device")
		}
		return r.buf, nil
	}

	r.buf = append(r.buf, chunk...)
	return nil, nil
}
