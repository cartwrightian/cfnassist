package tw.com.pictures.dot;

public class Edge extends HasAttributes {
	
	@Override
	public boolean equals(Object obj) {
		if (obj.getClass() != Edge.class) {
			return false;
		}
		Edge other = (Edge)obj;
		return (other.begin.equals(this.begin)) && (other.end.equals(this.end));
	}

	private String begin;
	private String end;
	
	public Edge(String begin, String end) {
		this.begin = begin;
		this.end = end;
	}

	public void write(Recorder recorder) {
		recorder.write(String.format("\"%s\"->\"%s\" ", begin, end));
		writeAttributes(recorder, false);
		recorder.writeline(";");
	}

	public Edge withLabel(String label) {
		addLabel(label);
		return this;
	}

	public Edge withNoArrow() {
		addNoDirection();
		return this;
	}
	
	public Edge withDot() {
		addDot();
		return this;
	}
	
	public Edge withDottedLine() {
		addDottedLine();
		return this;
	}

	public Edge endsAt(String elementId) {
		addEndsAt(elementId);
		return this;
	}

	public Edge withBox() {
		addBox();
		return this;	
	}

	public Edge beginsAt(String elementId) {
		addBeginsAt(elementId);
		return this;
	}

}
