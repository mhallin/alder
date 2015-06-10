'use strict';

var MIDIDispatch = require('./midi_dispatch');

var kMIDINoteModeRetrig = 'retrig';
var kMIDINoteModeLegato = 'legato';

var kMIDIPriorityHighest = 'highest';
var kMIDIPriorityLowest = 'lowest';
var kMIDIPriorityLastOn = 'last-on';

function MIDINoteNode(context) {
	this.gate = null;
	this.frequency = null;

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
		return device;
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
	this._lastVelocity = velocity;
	this._noteStack.push(note);

	if (this.frequency) {
		var portamentoEndTime = this.context.currentTime;

		if (this._noteStack.length > 1) {
			portamentoEndTime += this.portamento;
		}
		
		this.frequency.linearRampToValueAtTime(midiNoteToFrequency(note),
											   portamentoEndTime);
		this._lastNote = note;
	}

	if (this.gate && (this._noteStack.length === 1 ||
					  this.noteMode == kMIDINoteModeRetrig)) {
		this.gate.gate(velocity / 128);
	}
};

MIDINoteNode.prototype.dispatchNoteOff = function (note) {
	var noteIndex = this._noteStack.indexOf(note);
	this._noteStack.splice(noteIndex, 1);

	if (this._noteStack.length) {
		var nextNote = decideNextNote(this.priority, this._noteStack);

		if (this.noteMode === kMIDINoteModeRetrig &&
			nextNote != this._lastNote &&
			this.gate) {

			this.gate.gate(this._lastVelocity / 128);
		}

		if (this.frequency) {
			var portamentoEndTime = this.context.currentTime + this.portamento;
			this.frequency.linearRampToValueAtTime(midiNoteToFrequency(nextNote),
												   portamentoEndTime);
			this._lastNote = nextNote;
		}
	}
	else if (this.gate) {
		this.gate.gate(0);
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
