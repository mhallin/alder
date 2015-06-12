'use strict';

var MIDIDispatch = require('./midi_dispatch');

var kMIDINoteModeRetrig = 'retrig';
var kMIDINoteModeLegato = 'legato';

var kMIDIPriorityHighest = 'highest';
var kMIDIPriorityLowest = 'lowest';
var kMIDIPriorityLastOn = 'last-on';

function MIDINoteNode(context) {
	this._connectedGates = [];
	this._connectedFrequencies = [];

	this.context = context;
	this._device = null;
	this._noteStack = [];
	this._lastNote = null;
	this._lastVelocity = null;
}

MIDINoteNode.prototype.device = function (device) {
	if (device !== undefined) {
		if (this._device) {
			MIDIDispatch.removeMIDIEventListener(device, this);
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

MIDINoteNode.prototype.connect = function (destination, index) {
	if (index === 0) {
		this._connectedGates.push(destination);
	}
	else if (index === 1) {
		this._connectedFrequencies.push(destination);
	}
};

MIDINoteNode.prototype.disconnect = function (destination, index) {
	if (index === 0) {
		var gateIndex = this._connectedGates.indexOf(destination);

		if (gateIndex >= 0) {
			this._connectedGates.splice(gateIndex, 1);
		}
	}
	else if (index === 1) {
		var freqIndex = this._connectedFrequencies.indexOf(destination);

		if (freqIndex >= 0) {
			this._connectedFrequencies.splice(freqIndex, 1);
		}
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
	this._lastVelocity = velocity;
	this._noteStack.push(note);

	var portamentoEndTime = this.context.currentTime;

	if (this._noteStack.length > 1) {
		portamentoEndTime += this.portamento;
	}

	this.sendNote(note, portamentoEndTime);
	this._lastNote = note;

	if (this._noteStack.length === 1 || this.noteMode == kMIDINoteModeRetrig) {
		this.sendGate(velocity);
	}
};

MIDINoteNode.prototype.dispatchNoteOff = function (note) {
	var noteIndex = this._noteStack.indexOf(note);
	this._noteStack.splice(noteIndex, 1);

	if (this._noteStack.length) {
		var nextNote = decideNextNote(this.priority, this._noteStack);

		if (this.noteMode === kMIDINoteModeRetrig &&
			nextNote != this._lastNote) {

			this.sendGate(this._lastVelocity);
		}

		var portamentoEndTime = this.context.currentTime + this.portamento;
		this.sendNote(nextNote, portamentoEndTime);
		this._lastNote = nextNote;
	}
	else {
		this.sendGate(0);
	}
};

MIDINoteNode.prototype.sendGate = function (velocity) {
	for (var i = 0; i < this._connectedGates.length; ++i) {
		var param = this._connectedGates[i];

		param.gate(velocity / 128);
	}
};

MIDINoteNode.prototype.sendNote = function (note, portamentoEndTime) {
	for (var i = 0; i < this._connectedFrequencies.length; ++i) {
		var param = this._connectedFrequencies[i];

		param.linearRampToValueAtTime(midiNoteToFrequency(note),
									  portamentoEndTime);
	}
};

function midiNoteToFrequency(note) {
	return 440 * Math.pow(2, (note - 69) / 12);
}

function decideNextNote(priorityMode, activeNotes) {
	if (priorityMode === kMIDIPriorityLastOn) {
		return activeNotes[activeNotes.length - 1];
	}

	var highest = activeNotes[0];
	var lowest = activeNotes[0];

	for (var i = 0; i < activeNotes.length; ++i) {
		if (activeNotes[i] > highest) {
			highest = activeNotes[i];
		}

		if (activeNotes[i] < lowest) {
			lowest = activeNotes[i];
		}
	}

	return priorityMode === kMIDIPriorityHighest ? highest : lowest;
}

module.exports = MIDINoteNode;
