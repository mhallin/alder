'use strict';

var MIDIDispatch = require('./midi_dispatch');

function MIDICCNode(context) {
	this._connectedParams = [];
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
		this._connectedParams.push(destination);
	}
};

MIDICCNode.prototype.disconnect = function (destination, index) {
	if (index === 0) {
		var paramIndex = this._connectedParams.indexOf(destination);

		if (paramIndex >= 0) {
			this._connectedParams.splice(paramIndex, 1);
		}
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
	var time = this.context.currentTime;

	if (message === 0xb0 && channel === this.channel) {
		for (var i = 0; i < this._connectedParams.length; ++i) {
			var param = this._connectedParams[i];

			param.setValueAtTime(value / 128, this.context.currentTime);
		}
	}
};

module.exports = MIDICCNode;
