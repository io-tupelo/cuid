(ns cridx.core
  (:use tupelo.core)
  (:require
    [schema.core :as s]
    [tupelo.math :as math]
    [tupelo.schema :as tsk])
  (:import
    [java.util Random]))

;---------------------------------------------------------------------------------------------------
; BitScrambler
;  - idx->int
;  - idx->hex
;  - next-int
;  - next-hex
;
; new:  CRIDX - Cipher Random Index
; old:  CRUID - Cipher Random Unique ID

; #todo add fns:  next-biginteger, next-str-dec, next-str-hex

; vvv This must be 4 bits minimum
(def num-bits 64) ; 20 bits => 18 sec/(3xrounds) (ie 1M items)
(def num-rounds 2) ; must be non-zero!

(def num-dec-digits (long (Math/ceil (/ num-bits (math/log2 10)))))
(def num-hex-digits (long (Math/ceil (/ num-bits 4)))) ; (log2 16) => 4
(def N-max (math/pow-BigInteger 2 num-bits))
(def N-third (.divide N-max (biginteger 3)))
(spyx num-hex-digits)
(spyx num-bits)
(spyx N-max)

; #todo -> tupelo.math (incl unit tests)
(s/defn BigInteger->bitstr  :- s/Str
  [N :- s/Int
   width :- s/Int]
  (let [bitstr-raw  (.toString (biginteger N) 2)
        extra-chars (- width (count bitstr-raw))
        >>          (assert (int-nonneg? extra-chars))
        result      (strcat (repeat extra-chars \0) bitstr-raw)]
    result))

(when-not (<= 4 num-bits 160)
  (throw (ex-info "num-bits out of range [4..120]" (vals->map num-bits))))

;-----------------------------------------------------------------------------
; initialization

(def seed 777717777)
(def random
  (if false
    (Random. seed)
    (Random.)))

(def offset (it-> (.nextDouble random)
              (* it N-third)
              (+ it N-third)
              (biginteger it)))

(def increment ; ***** MUST BE ODD *****
  (biginteger
    (it-> (.nextDouble random)
      (* it N-third)
      (+ it N-third)
      (biginteger it)
      (if (even? it) ; ensure it is odd
          (.add ^BigInteger it (biginteger 1))
          it)
      )))

;-----------------------------------------------------------------------------
; sanity checks
(assert (pos-int? num-rounds))
(assert (biginteger? offset))
(assert (biginteger? increment))
(when-not (odd? increment) ; odd => relatively prime to 2^N
  (throw (ex-info "increment failed even test" (vals->map increment))))

(spyx [offset (math/BigInteger->binary-str offset)])
(spyx [increment (math/BigInteger->binary-str increment)])

;-----------------------------------------------------------------------------
(def bit-order
  (let [K            (Math/round (Math/sqrt num-bits))
        bit-seqs     (partition-all K (range num-bits))
        bit-seqs-rev (forv [[i bit-seq] (indexed bit-seqs)]
                       (if (odd? i)
                         (reverse bit-seq)
                         bit-seq))
        result       (vec (apply interleave-all bit-seqs-rev))] ; example [0 7 8 15 1 6 9 14 2 5 10 13 3 4 11 12]
    (assert (= (set (range num-bits)) (set result)))
    result))
(spyx bit-order)

(s/defn shuffle-bits :- BigInteger
  [arg :- BigInteger]
  (when-not (and (<= 0 arg) (< arg N-max))
    (throw (ex-info "arg out of range" (vals->map arg N-max))))
  (let [bits-orig         (math/BigInteger->binary-chars arg) ; does not include leading zeros
        num-bits-returned (count bits-orig)
        num-leading-zeros (- num-bits num-bits-returned)
        bits-full         (glue (repeat num-leading-zeros \0) bits-orig)
        bits-out          (forv [i (range num-bits)]
                            (let [isrc    (get bit-order i)
                                  bit-val (get bits-full isrc)]
                              bit-val))
        result            (math/binary-chars->BigInteger bits-out)]
    result))

(s/defn ^:no-doc idx-shuffle-round :- BigInteger
  [idx :- s/Int]
  (when-not (and (<= 0 idx) (< idx N-max))
    (throw (ex-info "arg out of range" (vals->map idx N-max))))
  (let [x1 (biginteger idx)
        x2 (.multiply ^BigInteger x1 increment)
        x3 (.add ^BigInteger offset x2)
        x4 (.mod ^BigInteger x3 N-max)
        x5 (shuffle-bits x4)]
    x5))

; Timing (2 rounds):
;   32 bits:  20 usec/call
;   64 bits:  40 usec/call
;  128 bits:  55 usec/call
(s/defn idx-shuffle :- BigInteger
  [idx :- s/Int]
  (biginteger
    (nth
      (iterate idx-shuffle-round idx) ; NOTE: seq is [x  (f x)  (f (f x))...] so don't use (dec N)
      num-rounds))) ;


