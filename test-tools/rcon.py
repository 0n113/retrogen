#!/usr/bin/env python3
"""Minimal local Source RCON client used by the integration test server."""

from __future__ import annotations

import socket
import struct
import sys

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "retrogen-local-test-only"


def packet(request_id: int, packet_type: int, body: str) -> bytes:
    payload = struct.pack("<ii", request_id, packet_type) + body.encode() + b"\0\0"
    return struct.pack("<i", len(payload)) + payload


def receive(sock: socket.socket) -> tuple[int, int, str]:
    length_data = sock.recv(4)
    if len(length_data) != 4:
        raise RuntimeError("RCON connection closed")
    length = struct.unpack("<i", length_data)[0]
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise RuntimeError("Incomplete RCON packet")
        data += chunk
    request_id, packet_type = struct.unpack("<ii", data[:8])
    return request_id, packet_type, data[8:-2].decode(errors="replace")


def run(command: str) -> str:
    with socket.create_connection((HOST, PORT), timeout=10) as sock:
        sock.settimeout(30)
        sock.sendall(packet(1, 3, PASSWORD))
        request_id, _, _ = receive(sock)
        if request_id == -1:
            raise RuntimeError("RCON authentication failed")
        sock.sendall(packet(2, 2, command))
        request_id, _, response = receive(sock)
        if request_id != 2:
            raise RuntimeError(f"Unexpected RCON request id: {request_id}")
        return response


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("usage: rcon.py <command>")
    print(run(" ".join(sys.argv[1:])))
