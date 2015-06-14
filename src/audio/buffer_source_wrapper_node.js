'use strict';

function BufferSourceWrapperNode(context) {
	this.context = context;

	this._node = context.createBufferSource();
	this.playbackRate = this._node.playbackRate;

	this.stopOnGateOff = null;
}

BufferSourceWrapperNode.prototype.connect = function (destination, output, input) {
	this._node.connect(destination, output, input);
};

BufferSourceWrapperNode.prototype.disconnect = function (destination, output, input) {
	this._node.disconnect(destination, output, input);
};

BufferSourceWrapperNode.prototype.loop = function (value) {
	if (value === undefined) {
		return this._node.loop;
	}
	else {
		this._node.loop = value;
	}
};

BufferSourceWrapperNode.prototype.loopStart = function (value) {
	if (value === undefined) {
		return this._node.loopStart;
	}
	else {
		this._node.loopStart = value;
	}
};

BufferSourceWrapperNode.prototype.loopEnd = function (value) {
	if (value === undefined) {
		return this._node.loopEnd;
	}
	else {
		this._node.loopEnd = value;
	}
};

BufferSourceWrapperNode.prototype.gate = function (value) {
	if (value > 0) {
		this._node.start();
	}
	else if (this.stopOnGateOff) {
		this._node.stop();
	}
};

module.exports = BufferSourceWrapperNode;
