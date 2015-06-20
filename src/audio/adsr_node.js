export default class ADSRNode {
	constructor(context) {
		this.attack = null;
		this.decay = null;
		this.sustain = null;
		this.release = null;

		this._connectedParams = [];
		this.context = context;
	}

	connect(param) {
		this._connectedParams.push(param);
		param.setValueAtTime(0, this.context.currentTime);
	}

	disconnect(param) {
		var index = this._connectedParams.indexOf(param);

		if (index >= 0) {
			this._connectedParams.splice(index, 1);
		}
	}

	gate(value) {
		if (value > 0) {
			this.noteOn(value);
		}
		else {
			this.noteOff(value);
		}
	}

	noteOn(value) {
		var now = this.context.currentTime;
		var decayStart = now + this.attack;
		var sustainStart = now + this.attack + this.decay;
		var sustainValue = value * this.sustain;

		for (var i = 0; i < this._connectedParams.length; ++i) {
			var param = this._connectedParams[i];

			param.cancelScheduledValues(now);
			param.setValueAtTime(0, now);
			param.linearRampToValueAtTime(value, decayStart);
			param.linearRampToValueAtTime(sustainValue, sustainStart);
		}
	}

	noteOff() {
		var now = this.context.currentTime;
		var releaseEnd = now + this.release;

		for (var i = 0; i < this._connectedParams.length; ++i) {
			var param = this._connectedParams[i];

			param.cancelScheduledValues(now);
			param.linearRampToValueAtTime(0, releaseEnd);
		}
	}
}
