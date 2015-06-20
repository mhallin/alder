class MIDIDispatch {
	constructor() {
		this.masterDevice = {
			id: "alder_master",
			name: "Master MIDI Input"
		};
		this._currentMasterDevice = null;

		this._devices = [this.masterDevice.id];

		this._eventListeners = {};
		this._eventListeners[this.masterDevice.id] = [];
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

	currentMasterDevice(device) {
		if (device === undefined) {
			return this._currentMasterDevice;
		}
		else {
			this._registerDevice(device);
			this._currentMasterDevice = device;
		}
	}

	addMIDIMessageEventListener(device, token, callback) {
		this._registerDevice(device);
		this._eventListeners[device.id].push([token, callback]);
	}

	removeMIDIMessageEventListener(device, token) {
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

		if (this.currentMasterDevice === device) {
			this.onMIDIMessage(this.masterDevice, event);
		}
	}
}

export default new MIDIDispatch();
