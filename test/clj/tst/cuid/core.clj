(ns tst.cuid.core
  (:use cuid.core
        tupelo.core
        tupelo.test)
  (:require
    [com.climate.claypoole :as cp]
    [cuid.crypt :as crypt]
    [tupelo.math :as math]
    [tupelo.profile :as prof]
    [tupelo.schema :as tsk]
    ))

(set! *warn-on-reflection* true)

(verify
  ; Fast coverage tests
  (doseq [nbits (thru 4 12)]
    (let [ctx (new-ctx {:num-bits nbits})]
      (with-map-vals ctx [N-max]
        (let [idx-vals    (range N-max)
              cuid-vals   (cp/pmap :builtin #(idx->cuid ctx %) idx-vals)
              idx-decrypt (cp/pmap :builtin #(cuid->idx ctx %) cuid-vals)]
          (is-set= idx-vals cuid-vals) ; all vals present
          (isnt= idx-vals cuid-vals) ; but not same order (random chance: 1 in N!)
          (is= idx-vals idx-decrypt) ; decryption recovers original vals, in order
          )))))

(verify
  ; ***** ENABLE TO SEE PRINTOUT *****
  (when false
    (let [ctx (new-ctx {:num-bits 32})]
      ; (spyx ctx)
      (with-map-vals ctx [num-bits N-max num-digits-dec num-digits-hex]
        (let [idx-vals    (take 32 (range N-max))
              cuid-vals   (mapv #(idx->cuid ctx %) idx-vals)
              idx-decrypt (mapv #(cuid->idx ctx %) cuid-vals)]
          (nl)
          (println (strcat "    idx      CUID        hex"
                     (repeat 16 \space) "binary"
                     (repeat 22 \space) "orig"))
          (doseq [[i cuid] (indexed cuid-vals)]
            (when (neg? cuid)
              (throw (ex-info "found-negative" (vals->map cuid))))
            (let [fmt-str (str "%7d   %0" num-digits-dec "d   %s   %s  %7d")
                  hex-str (math/BigInteger->hex-str cuid num-digits-hex)
                  bit-str (crypt/int->bitstr cuid num-bits)]
              (println (format fmt-str i cuid hex-str bit-str (nth idx-decrypt i)))))
          (isnt= idx-vals cuid-vals) ; but not same order (random chance 1 in N!)
          (is= idx-vals idx-decrypt))))))

(verify
  ; need to uncomment & reformat profile statements in source code to use this

  (when false ; round-trip timing printouts disabled by default

    (tsk/with-validation-disabled

      (prof/timer-stats-reset)
      (let [ctx (new-ctx {:num-bits 32})]
        (prn :timing-1000-32)
        (dotimes [i 1000] ; timing for 1000 CRIDX values
          (cuid->idx ctx
            (idx->cuid ctx i))))
      (prof/print-profile-stats)

      (prof/timer-stats-reset)
      (let [ctx (new-ctx {:num-bits 64})]
        (prn :timing-1000-64)
        (dotimes [i 1000] ; timing for 1000 CRIDX values
          (cuid->idx ctx
            (idx->cuid ctx i))))
      (prof/print-profile-stats)

      (prof/timer-stats-reset)
      (let [ctx (new-ctx {:num-bits 128})]
        (prn :timing-1000-128)
        (dotimes [i 1000] ; timing for 1000 CRIDX values
          (cuid->idx ctx
            (idx->cuid ctx i))))
      (prof/print-profile-stats)

      )))
