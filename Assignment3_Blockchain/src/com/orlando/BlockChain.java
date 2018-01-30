package com.orlando;
// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    private BlockInfo maxHeightBlockInfo;
    private final Block genesisBlock;
    private final TransactionPool pool = new TransactionPool();
    private final HashMap<ByteArrayWrapper, BlockInfo> blockHashToInfo = new HashMap<>();
    private final TreeSet<BlockInfo> blocks = new TreeSet<>();
    private final Consumer<Transaction> removeTxFromPool = tx -> pool.removeTransaction(tx.getHash());

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        this.genesisBlock = genesisBlock;
        final ByteArrayWrapper blockHash = new ByteArrayWrapper(genesisBlock.getHash());
        final UTXOPool initialUTXOPool = new UTXOPool();
        final ArrayList<Transaction> txs = new ArrayList<>(genesisBlock.getTransactions());
        txs.add(genesisBlock.getCoinbase());
        txs.stream().forEach(tx -> addTxOutputsToUTXOPool(initialUTXOPool, tx));
        maxHeightBlockInfo = new BlockInfo(0, genesisBlock, initialUTXOPool);
        blockHashToInfo.put(blockHash, maxHeightBlockInfo);
        blocks.add(maxHeightBlockInfo);
    }


    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightBlockInfo.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return maxHeightBlockInfo.utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return pool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        // IMPLEMENT THIS
        final ByteArrayWrapper hash = new ByteArrayWrapper(block.getHash());
        final Optional<byte[]> maybeRawPrevHash = Optional.ofNullable(block.getPrevBlockHash());
        final Optional<BlockInfo> maybePrevInfo = maybeRawPrevHash.map(prevHash -> blockHashToInfo.get(new ByteArrayWrapper(prevHash)));
        final Optional<BlockInfo> maybeNewInfo = maybePrevInfo.flatMap(prevInfo -> {
            final UTXOPool prevUtxoPool = prevInfo.utxoPool;
            final TxHandler txHandler = new TxHandler(prevUtxoPool);
            final ArrayList<Transaction> txs = new ArrayList<>(block.getTransactions());
            final Transaction[] validTxs = txHandler.handleTxs(txs.toArray(new Transaction[txs.size()]));
            final Boolean blockTxsAreValid = validTxs.length == txs.size();
            final UTXOPool newUTXOPool = txHandler.getUTXOPool();
            final Integer newHeight = prevInfo.height + 1;
            final BlockInfo newInfo = new BlockInfo(newHeight, block, newUTXOPool);
            return blockTxsAreValid ? Optional.of(newInfo) : Optional.empty();
        });
        final Optional<Boolean> maybeSuccessAdd = maybeNewInfo.map(newInfo -> {
            final Boolean isNewMax = newInfo.height > maxHeightBlockInfo.height;
            maxHeightBlockInfo = isNewMax ? newInfo : maxHeightBlockInfo;
            addTxOutputsToUTXOPool(newInfo.utxoPool, block.getCoinbase());
            blockHashToInfo.put(hash, newInfo);
            blocks.add(newInfo);
            return true;
        });
        maybePrevInfo.ifPresent(info -> removeTxsFromBlock(info.block));
        maybeNewInfo.ifPresent(info -> addTxsFromBlock(info.block));
        cleanupBlockInfos();
        return maybeSuccessAdd.orElse(false);
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        this.pool.addTransaction(tx);
    }

    private void addTxsFromBlock(Block block) {
        final ArrayList<Transaction> txs = new ArrayList<>(block.getTransactions());
        txs.add(block.getCoinbase());
        txs.stream().forEach(pool::addTransaction);
    }

    private void removeTxsFromBlock(Block block) {
        final ArrayList<Transaction> txs = new ArrayList<>(block.getTransactions());
        txs.add(block.getCoinbase());
        txs.stream().forEach(removeTxFromPool);
    }

    private void cleanupBlockInfos() {
        final Integer cutoffHeight = maxHeightBlockInfo.height - CUT_OFF_AGE;
        final BlockInfo dummyBlockInfo = new BlockInfo(cutoffHeight, genesisBlock, new UTXOPool());
        SortedSet<BlockInfo> oldBlocks = blocks.headSet(dummyBlockInfo);
        Iterator<BlockInfo> iterator = oldBlocks.iterator();
        while(iterator.hasNext()) {
            BlockInfo oldBlockInfo = iterator.next();
            blockHashToInfo.remove(new ByteArrayWrapper(oldBlockInfo.block.getHash()));
            iterator.remove();
        }
    }

    private void addTxOutputsToUTXOPool(UTXOPool pool, Transaction tx) {
        final Consumer<Integer> addNewUTXO = index -> {
            final Transaction.Output newOutput = tx.getOutput(index);
            final UTXO newUTXO = new UTXO(tx.getHash(), index);
            pool.addUTXO(newUTXO, newOutput);
        };
        final Stream<Integer> outputIndices = IntStream.range(0, tx.numOutputs()).boxed();
        outputIndices.collect(Collectors.toList()).forEach(addNewUTXO);
    }

    private static class BlockInfo implements Comparable<BlockInfo> {
        public final Integer height;
        public final Block block;
        public final UTXOPool utxoPool;

        public BlockInfo(Integer height, Block block, UTXOPool utxoPool) {
            this.height = height;
            this.block = block;
            this.utxoPool = utxoPool;
        }

        @Override
        public int compareTo(BlockInfo o) {
            final int heightCompare = height.compareTo(o.height);
            return heightCompare != 0 ? heightCompare : Integer.compare(block.hashCode(), o.block.hashCode());
        }
    }
}

