export default class JSSourceNode {
	constructor(context) {
		this.context = context;

		this._source = null;
		this._callback = null;
		this._connections = [];
	}

	source(value) {
		if (value === undefined) {
			return this._source;
		}
		else {
			this._source = value;
			this.rebuildModule();
		}
	}

	connect(destination, output, input) {
		if (output === 0) {
			this._connections.push([destination, input]);
			destination[input](this._callback);
		}
	}

	disconnect(destination, output, input) {
		if (output === 0) {
			for (var i = 0; i < this._connections.length; ++i) {
				var connection = this._connections[i];

				if (connection[0] === destination &&
					connection[1] === input) {
					destination[input](null);
					this._connections.splice(i, 1);
					break;
				}
			}
		}
	}

	rebuildModule() {
		var callback = null;

		if (this._source) {
			var module = {exports: null};
			eval(this._source);

			if (module.exports) {
				var instance = new (module.exports)(this.context);

				callback = instance.onaudioprocess.bind(instance);
			}
		}

		this.assignCallback(callback);
	}

	assignCallback(callback) {
		this._callback = callback;

		for (var i = 0; i < this._connections.length; ++i) {
			var connection = this._connections[0];
			var destination = connection[0];
			var input = connection[1];

			destination[input](callback);
		}
	}
}
