package net.osmand.plus.measurementtool.command;

import net.osmand.plus.measurementtool.MeasurementToolLayer;

import java.util.Collections;

public class ReorderPointCommand implements Command {

	private final MeasurementToolLayer measurementLayer;
	private final int from;
	private final int to;

	public ReorderPointCommand(MeasurementToolLayer measurementLayer, int from, int to) {
		this.measurementLayer = measurementLayer;
		this.from = from;
		this.to = to;
	}

	@Override
	public boolean execute() {
		return true;
	}

	@Override
	public void undo() {
		swap();
	}

	@Override
	public void redo() {
		swap();
	}

	private void swap() {
		// todo: fix exception
		try {
			Collections.swap(measurementLayer.getMeasurementPoints(), from, to);
		} catch (Exception e) {
			// index out of bounds
			// an exception occurs when to == -1 basically
			// but maybe from == measurementPoints.size() sometimes so there is an exception
		}
		measurementLayer.refreshMap();
	}
}
