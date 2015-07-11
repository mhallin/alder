export default class LatchNode {
	constructor(context) {
		this.context = context;

		this._connectedGates = [];
		this._value = 0;
	}

	connect(destination, output, input) {
		if (output === 0) {
			this._connectedGates.push([destination, input]);
			destination[input](this._value);
		}
	}

	disconnect(destination, output, input) {
		if (output === 0) {
			for (var i = 0; i < this._connectedGates.length; ++i) {
				var connection = this._connectedGates[i];

				if (connection[0] == destination &&
					connection[1] == input) {
					if (this._value > 0) {
						destination[input](0);
					}

					this._connectedGates.splice(i, 1);
					break;
				}
			}
		}
	}

	gate(value) {
		if (value > 0) {
			if (this._value > 0) {
				this._value = 0;
			}
			else {
				this._value = value;
			}

			this.sendGateValue();
		}
	}

	sendGateValue() {
		for (var i = 0; i < this._connectedGates.length; ++i) {
			var connection = this._connectedGates[i];
			var destination = connection[0];
			var input = connection[1];

			destination[input](this._value);
		}
	}
}
