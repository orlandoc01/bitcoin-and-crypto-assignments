package com.orlando;
import com.orlando.Crypto;
import com.orlando.Transaction;
import com.orlando.UTXO;
import com.orlando.UTXOPool;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TxHandler {
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    private UTXOPool utxoPool;
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }
    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        // IMPLEMENT THIS
      final Predicate<Transaction.Input> isInUTXOPool = input -> this.utxoPool.contains(new UTXO(input.prevTxHash, input.outputIndex));
      final Function<Transaction.Input, UTXO> makeUTXO = input -> new UTXO(input.prevTxHash, input.outputIndex);
      final Function<Transaction.Input, Transaction.Output> getPrevOutput = makeUTXO.andThen(utxo -> this.utxoPool.getTxOutput(utxo));
      final Predicate<Transaction.Output> isNonNegative = output -> output.value >= 0;
      final BiPredicate<Integer, Transaction.Input> isSigned = (index, txInput) -> {
        final byte[] message = tx.getRawDataToSign(index);
        final PublicKey pubKey = getPrevOutput.apply(txInput).address;
        final byte[] signature = txInput.signature;
        return Crypto.verifySignature(pubKey, message, signature);
      };


      final ArrayList<Transaction.Input> txInputs = tx.getInputs();
      return Optional.of(txInputs)
        //Criteria 1
        .filter(txIns -> txIns.stream().allMatch(isInUTXOPool))
        //Criteria 2
        .filter(txIns -> txIns.stream().allMatch(txIn -> isSigned.test(txInputs.indexOf(txIn), txIn)))
        .map(txIns -> txIns.stream().map(getPrevOutput).collect(Collectors.toList()))
        //Criteria 3
        .filter(outputs -> outputs.stream().count() == outputs.stream().distinct().count())
        .map(outputs -> outputs.stream().map(output -> output.value).collect(Collectors.toList()))
        .map(values -> values.stream().reduce(0.0, Double::sum))
        .flatMap(inputSum -> {
          return Optional.of(tx.getOutputs())
            //Criteria 4
            .filter(txOuts -> txOuts.stream().allMatch(isNonNegative))
            .map(txOuts -> txOuts.stream().map(txOut -> txOut.value).collect(Collectors.toList()))
            .map(values -> values.stream().reduce(0.0, Double::sum))
            //Criteria 5
            .map(outputSum -> inputSum >= outputSum);
        }).orElse(Boolean.FALSE);
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
      // IMPLEMENT THIS
      final ArrayList<Transaction> validTransactions = new ArrayList<>();
      final Consumer<Transaction> addTxAndUpdateUTXO = tx -> {
        final Consumer<Transaction.Input> spendUTXO = input -> {
          final UTXO oldUTXO = new UTXO(input.prevTxHash, input.outputIndex);
          this.utxoPool.removeUTXO(oldUTXO);
        };
        final Consumer<Integer> addNewUTXO = index -> {
          final Transaction.Output newOutput = tx.getOutput(index);
          final UTXO newUTXO = new UTXO(tx.getHash(), index);
          this.utxoPool.addUTXO(newUTXO, newOutput);
        };
        final ArrayList<Transaction.Input> inputs = tx.getInputs();
        inputs.forEach(spendUTXO);
        final Stream<Integer> outputIndices = IntStream.range(0, tx.numOutputs()).boxed();
        outputIndices.collect(Collectors.toList()).forEach(addNewUTXO);
        validTransactions.add(tx);
      };

      for (Transaction tx : possibleTxs) {
        Optional.of(tx).filter(this::isValidTx).ifPresent(addTxAndUpdateUTXO);
      }
      Transaction[] returnedValidTransactions = new Transaction[validTransactions.size()];
      return validTransactions.toArray(returnedValidTransactions);
    }

}
