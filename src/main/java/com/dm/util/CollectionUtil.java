package com.dm.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class CollectionUtil {

    public interface IList<T, R> {
        R collect(T t);
    }

    public interface IIterate<T> {
        void process(T t);
    }

    public interface Filter<T> {
        boolean accept(T t);
    }

    public static <T, R> List<R> collect(Collection<T> collection, IList<T, R> callback) {
        List<R> result = new ArrayList<>();
        for (T t : collection) {
            result.add(callback.collect(t));
        }
        return result;
    }

    public static <T> List<T> filter(Collection<T> collection, Filter<T> filter) {
        List<T> result = new ArrayList<>();
        for (T t : collection) {
            if (filter.accept(t)) {
                result.add(t);
            }
        }
        return result;
    }

    public static <T> void each(Iterator<T> iterator, IIterate<T> callback) {
        while (iterator.hasNext()) {
            callback.process(iterator.next());
        }
    }
}