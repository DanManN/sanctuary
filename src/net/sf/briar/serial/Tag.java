package net.sf.briar.serial;

interface Tag {

	byte FALSE = (byte) 0xFF; // 1111 1111
	byte TRUE = (byte) 0xFE; // 1111 1110
	byte INT8 = (byte) 0xFD; // 1111 1101
	byte INT16 = (byte) 0xFC; // 1111 1100
	byte INT32 = (byte) 0xFB; // 1111 1011
	byte INT64 = (byte) 0xFA; // 1111 1010
	byte FLOAT32 = (byte) 0xF9; // 1111 1001
	byte FLOAT64 = (byte) 0xF8; // 1111 1000
	byte STRING = (byte) 0xF7; // 1111 0111
	byte BYTES = (byte) 0xF6; // 1111 0110
	byte LIST = (byte) 0xF5; // 1111 0111
	byte MAP = (byte) 0xF4; // 1111 0100
	byte END = (byte) 0xF3; // 1111 0011
	byte NULL = (byte) 0xF2; // 1111 0010
	byte STRUCT = (byte) 0xF1; // 1111 0001

	int SHORT_STRING = 0x80; // 1000 xxxx
	int SHORT_BYTES = 0x90; // 1001 xxxx
	int SHORT_LIST = 0xA0; // 1010 xxxx
	int SHORT_MAP = 0xB0; // 1011 xxxx
	int SHORT_STRUCT = 0xC0; // 110x xxxx

	int SHORT_MASK = 0xF0; // Match first four bits
	int SHORT_STRUCT_MASK = 0xE0; // Match first three bits
}