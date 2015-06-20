import MIDIDispatch from './midi_dispatch';

export default class MIDICCNode {
	constructor(context) {
		this._connectedParams = [];
		this.channel = null;

		this.context = context;
		this._device = null;
	}

	device(device) {
		if (device !== undefined) {
			if (this._device) {
				MIDIDispatch.removeMIDIMessageEventListener(this._device, this);
				this._device = null;
			}

			this._device = device;

			if (this._device) {
				MIDIDispatch.addMIDIMessageEventListener(device, this, this.onMIDIMessage);
			}
		}
		else {
			return this._device;
		}
	}

	connect(destination, index) {
		if (index === 0) {
			this._connectedParams.push(destination);
		}
	}

	disconnect(destination, index) {
		if (index === 0) {
			var paramIndex = this._connectedParams.indexOf(destination);

			if (paramIndex >= 0) {
				this._connectedParams.splice(paramIndex, 1);
			}
		}
	}

	onMIDIMessage(event) {
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
	}
}
