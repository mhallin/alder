'use strict';

var MIDIDispatch = require('./midi_dispatch');

function MIDICCNode(context) {
	this.param = null;
	this.channel = null;

	this.context = context;
	this._device = null;
}

MIDICCNode.prototype.device = function (device) {
	if (device !== undefined) {
		if (this._device) {
			MIDIDispatch.removeMIDIEventListener(this._device, this);
			this._device = null;
		}

		this._device = device;

		if (this._device) {
			MIDIDispatch.addMIDIEventListener(device, this, this.onmidimessage);
		}
	}
	else {
		return this._device;
	}
};

MIDICCNode.prototype.connect = function (destination, index) {
	if (index === 0) {
		this.param = destination;
	}
};

MIDICCNode.prototype.disconnect = function (destination, index) {
	if (index === 0) {
		this.param = null;
	}
};

MIDICCNode.prototype.onmidimessage = function (event) {
	var data = event.data;

	if (data.length < 3) {
		return;
	}

	var message = data[0] & 0xf0;
	var channel = data[1] & 0x7f;
	var value = data[2] & 0x7f;

	if (this.param && message === 0xb0 && channel === this.channel) {
		this.param.setValueAtTime(value / 128, this.context.currentTime);
	}
};

module.exports = MIDICCNode;
