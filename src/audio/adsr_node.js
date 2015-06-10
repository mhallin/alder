'use strict';

function ADSRNode(context) {
	this.attack = null;
	this.decay = null;
	this.sustain = null;
	this.release = null;

	this.param = null;
	this.context = context;
}

ADSRNode.prototype.connect = function (param) {
	this.param = param;
	this.param.setValueAtTime(0, this.context.currentTime);
};

ADSRNode.prototype.disconnect = function (param) {
	this.param = null;
};

ADSRNode.prototype.gate = function (value) {
	if (!this.param) {
		return;
	}

	if (value > 0) {
		this.noteOn(value);
	}
	else {
		this.noteOff(value);
	}
};

ADSRNode.prototype.noteOn = function (value) {
	if (!this.param) {
		return;
	}
	
	var now = this.context.currentTime;
	var decayStart = now + this.attack;
	var sustainStart = now + this.attack + this.decay;
	var sustainValue = value * this.sustain;

	this.param.cancelScheduledValues(now);
	this.param.setValueAtTime(0, now);
	this.param.linearRampToValueAtTime(value, decayStart);
	this.param.linearRampToValueAtTime(sustainValue, sustainStart);
};

ADSRNode.prototype.noteOff = function () {
	if (!this.param) {
		return;
	}

	var now = this.context.currentTime;
	var releaseEnd = now + this.release;

	this.param.cancelScheduledValues(now);
	this.param.linearRampToValueAtTime(0, releaseEnd);
};

module.exports = ADSRNode;
