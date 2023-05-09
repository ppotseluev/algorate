package com.github.ppotseluev.algorate.ta4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

import java.util.function.Function;

/**
 * Copy-paste from ta4j with dynamical tradeAmount calculation supported
 * <p>
 * A manager for {@link BarSeries} objects.
 * <p>
 * Used for backtesting. Allows to run a {@link Strategy trading strategy} over
 * the managed bar series.
 */
public class BarSeriesManager {

    /**
     * The logger
     */
    private static final Logger log = LoggerFactory.getLogger(org.ta4j.core.BarSeriesManager.class);

    /**
     * The managed bar series
     */
    private BarSeries barSeries;

    /**
     * The trading cost models
     */
    private CostModel transactionCostModel;
    private CostModel holdingCostModel;

    /**
     * Constructor.
     */
    public BarSeriesManager() {
        this(null, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param barSeries the bar series to be managed
     */
    public BarSeriesManager(BarSeries barSeries) {
        this(barSeries, new ZeroCostModel(), new ZeroCostModel());
    }

    /**
     * Constructor.
     *
     * @param barSeries            the bar series to be managed
     * @param transactionCostModel the cost model for transactions of the asset
     * @param holdingCostModel     the cost model for holding asset (e.g. borrowing)
     */
    public BarSeriesManager(BarSeries barSeries, CostModel transactionCostModel, CostModel holdingCostModel) {
        this.barSeries = barSeries;
        this.transactionCostModel = transactionCostModel;
        this.holdingCostModel = holdingCostModel;
    }

    /**
     * @param barSeries the bar series to be managed
     */
    public void setBarSeries(BarSeries barSeries) {
        this.barSeries = barSeries;
    }

    /**
     * @return the managed bar series
     */
    public BarSeries getBarSeries() {
        return barSeries;
    }

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * Opens the position with a {@link TradeType} BUY trade.
     *
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy) {
        return run(strategy, TradeType.BUY);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     * <p>
     * Opens the position with a {@link TradeType} BUY trade.
     *
     * @param strategy    the trading strategy
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, int startIndex, int finishIndex) {
        return run(strategy, TradeType.BUY, b -> barSeries.numOf(1), startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * Opens the position with a trade of {@link TradeType tradeType}.
     *
     * @param strategy  the trading strategy
     * @param tradeType the {@link TradeType} used to open the position
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType) {
        return run(strategy, tradeType, b -> barSeries.numOf(1));
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     * <p>
     * Opens the position with a trade of {@link TradeType tradeType}.
     *
     * @param strategy    the trading strategy
     * @param tradeType   the {@link TradeType} used to open the position
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, int startIndex, int finishIndex) {
        return run(strategy, tradeType, b -> barSeries.numOf(1), startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     *
     * @param strategy  the trading strategy
     * @param tradeType the {@link TradeType} used to open the position
     * @param amount    the amount used to open/close the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, Function<Bar, Num> amount) {
        return run(strategy, tradeType, amount, barSeries.getBeginIndex(), barSeries.getEndIndex());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     *
     * @param strategy    the trading strategy
     * @param tradeType   the {@link TradeType} used to open the trades
     * @param amount      the amount used to open/close the trades
     * @param startIndex  the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, TradeType tradeType, Function<Bar, Num> amount, int startIndex, int finishIndex) {

        int runBeginIndex = Math.max(startIndex, barSeries.getBeginIndex());
        int runEndIndex = Math.min(finishIndex, barSeries.getEndIndex());

        log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex, strategy,
                tradeType);
        TradingRecord tradingRecord = new BaseTradingRecord(tradeType, transactionCostModel, holdingCostModel);
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            // For each bar between both indexes...
            if (strategy.shouldOperate(i, tradingRecord)) {
                Bar bar = barSeries.getBar(i);
                Num lots;
                Position currentPosition = tradingRecord.getCurrentPosition();
                if (currentPosition.isOpened()) {
                    lots = currentPosition.getEntry().getAmount();
                } else {
                    lots = amount.apply(bar);
                }
                if (lots.isPositive()) {
                    tradingRecord.operate(i, bar.getClosePrice(), lots);
                }
            }
        }

        if (!tradingRecord.isClosed()) {
            // If the last position is still opened, we search out of the run end index.
            // May works if the end index for this run was inferior to the actual number of
            // bars
            int seriesMaxSize = Math.max(barSeries.getEndIndex() + 1, barSeries.getBarData().size());
            for (int i = runEndIndex + 1; i < seriesMaxSize; i++) {
                // For each bar after the end index of this run...
                // --> Trying to close the last position
                if (strategy.shouldOperate(i, tradingRecord)) {
                    Bar bar = barSeries.getBar(i);
                    Num lots = tradingRecord.getCurrentPosition().getEntry().getAmount();
                    tradingRecord.operate(i, bar.getClosePrice(), lots);
                    break;
                }
            }
        }
        return tradingRecord;
    }

}
