'use strict';

function MIDINoteNode(context) {
	this.gate = null;
	this.frequency = null;

	this.context = context;
	this._device = null;
}

MIDINoteNode.prototype.device = function (device) {
	if (device !== undefined) {
		if (this._device) {
			this._device.onmidimessage = null;
			this._device = null;
		}

		this._device = device;

		if (this._device) {
			var self = this;
			this._device.onmidimessage = function (e) { self.onmidimessage(e); };
		}
	}
};

MIDINoteNode.prototype.connect = function (destination, index) {
	if (index === 0) {
		this.gate = destination;
	}
	else if (index === 1) {
		this.frequency = destination;
	}
};

MIDINoteNode.prototype.disconnect = function (destination, index) {
	if (index === 0) {
		this.gate = null;
	}
	else if (index === 1) {
		this.frequency = null;
	}
};

MIDINoteNode.prototype.onmidimessage = function (event) {
	var data = event.data;

	if (data.length < 3) {
		return;
	}

	var message = data[0] & 0xf0;
	var note = data[1] & 0x7f;
	var velocity = data[2] & 0x7f;

	if (message === 0x90 && velocity > 0) {
		this.dispatchNoteOn(note, velocity);
	}
	else if (message === 0x80 || (message === 0x90 && velocity === 0)) {
		this.dispatchNoteOff(note);
	}
};

MIDINoteNode.prototype.dispatchNoteOn = function (note, velocity) {
	if (this.frequency) {
		this.frequency.linearRampToValueAtTime(midiNoteToFrequency(note),
											   this.context.currentTime);
	}

	if (this.gate) {
		this.gate.gate(velocity / 128);
	}
};

MIDINoteNode.prototype.dispatchNoteOff = function (note) {
	if (this.gate) {
		this.gate.gate(0);
	}
};

function midiNoteToFrequency(note) {
	return 440 * Math.pow(2, (note - 69) / 12);
}

module.exports = MIDINoteNode;
