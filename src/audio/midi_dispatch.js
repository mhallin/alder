class MIDIDispatch {
	constructor() {
		this._devices = [];

		this._eventListeners = {};
	}

	_registerDevice(device) {
		if (this._devices.indexOf(device.id) !== -1) {
			return;
		}

		this._devices.push(device.id);
		this._eventListeners[device.id] = [];

		var self = this;
		device.onmidimessage = function(e) { self.onMIDIMessage(device, e); };
	}

	addMIDIEventListener(device, token, callback) {
		this._registerDevice(device);
		this._eventListeners[device.id].push([token, callback]);
	}

	removeMIDIEventListener(device, token) {
		this._registerDevice(device);

		var listenerArray = this._eventListeners[device.id];

		for (var i = 0; i < listenerArray.length; ++i) {
			if (listenerArray[i][0] === token) {
				listenerArray.splice(i, 1);
				break;
			}
		}
	}

	onMIDIMessage(device, event) {
		var listenerArray = this._eventListeners[device.id];

		for (var i = 0; i < listenerArray.length; ++i) {
			var token = listenerArray[i][0];
			var callback = listenerArray[i][1];

			callback.call(token, event);
		}
	}
}

export default new MIDIDispatch();
