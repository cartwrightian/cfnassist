package tw.com.repository;

import tw.com.entity.OutputLogEventDecorator;

import java.util.*;
import java.util.stream.Stream;

public class LogStreamInterleaver implements Iterable<OutputLogEventDecorator> {

    private final List<Iterator<OutputLogEventDecorator>> iteratorList;
    private int streamIndex;

    // NOPE :-(
    // ASSUME: streams are ordered earliest first and each stream has earlier events first
    public LogStreamInterleaver(List<Stream<OutputLogEventDecorator>> rawStreams) {
        this.iteratorList = new LinkedList<>();
        rawStreams.forEach(rawStream -> iteratorList.add(rawStream.iterator()));
        streamIndex = 0;
    }

    private boolean hasAnyLeft() {
        if (iteratorList.get(streamIndex).hasNext()) {
            return true;
        }
        streamIndex++;
        if (streamIndex>=iteratorList.size()) {
            return false;
        }
        return iteratorList.get(streamIndex).hasNext();
    }

    public OutputLogEventDecorator getNext() {
        return iteratorList.get(streamIndex).next();
    }

    @Override
    public Iterator<OutputLogEventDecorator> iterator() {
        return new Iterator<OutputLogEventDecorator>() {
            @Override
            public boolean hasNext() {
                return hasAnyLeft() ;
            }

            @Override
            public OutputLogEventDecorator next() {
                return getNext();
            }
        };
    }

}
