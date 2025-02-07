package com.jstarcraft.core.common.bloomfilter;

import java.util.Random;

import com.jstarcraft.core.common.bit.BitMap;
import com.jstarcraft.core.common.hash.HashFunction;
import com.jstarcraft.core.common.hash.StringHashFunction;

public class BitMapBloomFilter<E, M extends BitMap<?>> implements BloomFilter<E, M> {

    protected M bits;

    protected HashFunction<E>[] functions;

    protected static StringHashFunction[] getFunctions(StringHashFamily hashFamily, int hashSize, Random random) {
        StringHashFunction[] functions = new StringHashFunction[hashSize];
        for (int index = 0; index < hashSize; index++) {
            functions[index] = hashFamily.getHashFunction(random);
        }
        return functions;
    }

    public BitMapBloomFilter(M bits, HashFunction<E>... functions) {
        this.bits = bits;
        this.functions = functions;
    }

    @Override
    public int getElements(E... datas) {
        int count = 0;
        int capacity = bits.capacity();
        int size = datas.length * hashSize();
        int[] indexes = new int[size];
        boolean[] values = new boolean[size];
        int cursor = 0;
        for (E data : datas) {
            for (HashFunction<E> function : functions) {
                int hash = function.hash(data);
                int index = Math.abs(hash % capacity);
                indexes[cursor++] = index;
            }
        }
        bits.get(indexes, values);
        cursor = 0;
        for (E data : datas) {
            boolean hit = true;
            for (HashFunction<E> function : functions) {
                if (!values[cursor++]) {
                    hit = false;
                }
            }
            if (hit) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void putElements(E... datas) {
        int capacity = bits.capacity();
        int size = datas.length * hashSize();
        int[] indexes = new int[size];
        int cursor = 0;
        for (E data : datas) {
            for (HashFunction<E> function : functions) {
                int hash = function.hash(data);
                int index = Math.abs(hash % capacity);
                indexes[cursor++] = index;
            }
        }
        bits.set(indexes);
    }

    @Override
    public int bitSize() {
        return bits.capacity();
    }

    @Override
    public int hashSize() {
        return functions.length;
    }

}
