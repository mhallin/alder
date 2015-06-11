'use strict';

function MIDIDispatch() {
	this._devices = [];

	this._eventListeners = {};
}

MIDIDispatch.prototype._registerDevice = function (device) {
	if (this._devices.indexOf(device) !== -1) {
		return;
	}

	this._devices.push(device);
	this._eventListeners[device.id] = [];

	var self = this;
	device.onmidimessage = function(e) { self.onMIDIMessage(device, e); };
};

MIDIDispatch.prototype.addMIDIEventListener = function (device, token, callback) {
	this._registerDevice(device);
	this._eventListeners[device.id].push([token, callback]);
};

MIDIDispatch.prototype.removeMIDIEventListener = function (device, token) {
	this._registerDevice(device);

	var listenerArray = this._eventListeners[device.id];

	for (var i = 0; i < listenerArray.length; ++i) {
		if (listenerArray[i][0] === token) {
			listenerArray.splice(i, 1);
			break;
		}
	}
};

MIDIDispatch.prototype.onMIDIMessage = function (device, event) {
	var listenerArray = this._eventListeners[device.id];

	for (var i = 0; i < listenerArray.length; ++i) {
		var token = listenerArray[i][0];
		var callback = listenerArray[i][1];

		callback.call(token, event);
	}
};

module.exports = new MIDIDispatch();
