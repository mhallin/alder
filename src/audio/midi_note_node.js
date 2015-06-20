'use strict';

import MIDIDispatch from './midi_dispatch';

const kMIDINoteModeRetrig = 'retrig';
const kMIDINoteModeLegato = 'legato';

const kMIDIPriorityHighest = 'highest';
const kMIDIPriorityLowest = 'lowest';
const kMIDIPriorityLastOn = 'last-on';

export default class MIDINoteNode {
	constructor (context) {
		this._connectedGates = [];
		this._connectedFrequencies = [];

		this.context = context;
		this._device = null;
		this._noteStack = [];
		this._lastNote = null;
		this._lastVelocity = null;
	}

	device(device) {
		if (device !== undefined) {
			if (this._device) {
				MIDIDispatch.removeMIDIMessageEventListener(device, this);
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
			this._connectedGates.push(destination);
		}
		else if (index === 1) {
			this._connectedFrequencies.push(destination);
		}
	}

	disconnect(destination, index) {
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
	}

	onMIDIMessage(event) {
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
	}

	dispatchNoteOn(note, velocity) {
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
	}

	dispatchNoteOff(note) {
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
	}

	sendGate(velocity) {
		for (var i = 0; i < this._connectedGates.length; ++i) {
			var param = this._connectedGates[i];

			param.gate(velocity / 128);
		}
	}

	sendNote(note, portamentoEndTime) {
		for (var i = 0; i < this._connectedFrequencies.length; ++i) {
			var param = this._connectedFrequencies[i];

			param.linearRampToValueAtTime(midiNoteToFrequency(note),
										  portamentoEndTime);
		}
	}
}

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
