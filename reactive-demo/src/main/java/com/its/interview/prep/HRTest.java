package com.its.interview.prep;

import java.util.ArrayList;
import java.util.List;

// ==========================================================
// These comments will create merge conflicts
// And thereby will have to be resolved
// ==========================================================
public class HRTest {

    /**
     * New method level comments
     * done  directly into Main
     * @param args
     */
    public static void main(String [] args) {
        List<Integer> integerList = new ArrayList<Integer>();
        integerList.add(1);

        integerList.add(2);

        integerList.add(3);

        findNumber(integerList, 4);

        System.out.println(" list : " + integerList);

    }

    static String findNumber(List<Integer> arr, int k) {
        if (arr.contains(k)) {
            return "YES";
        }
        else {
            return "NO";
        }



    }
}
