'use strict';

function ConstSourceNode(context) {
	this.buffer = context.createBuffer(1, 1, context.sampleRate);

	this.node = context.createBufferSource();
	this.node.buffer = this.buffer;
	this.node.loop = true;
	this.node.start();
}

ConstSourceNode.prototype.value = function(value) {
	if (value === undefined) {
		return this.buffer.getChannelData(0)[0];
	}
	else {
		this.buffer.getChannelData(0)[0] = value;
	}
};

ConstSourceNode.prototype.connect = function(destination, output, input) {
	if (output === 0) {
		this.node.connect(destination, input);
	}
};

ConstSourceNode.prototype.disconnect = function(output) {
	if (output === 0) {
		this.node.disconnect();
	}
};

module.exports = ConstSourceNode;
