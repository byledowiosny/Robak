package akatom;

/**
 * Created by jerzy on 27/02/17.
 */

import java.util.Arrays;
import java.util.List;

import com.dukascopy.api.*;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.JFException;

import com.jforex.programming.order.OrderParams;
import com.jforex.programming.order.task.params.basic.MergeParams;
import com.jforex.programming.order.task.params.basic.SubmitParams;
import com.jforex.programming.strategy.JForexUtilsStrategy;

@RequiresFullAccess
public class OnLucky extends JForexUtilsStrategy 
{
    private IEngine engine;
    private IHistory history;

    private long lastTickTime = 0;
    private double lastProfitLossInPips_EURUSD = 0;
    private double lastProfitLossInPips_GBPUSD = 0;
    private double baseAmound = 0.01;
    private int counter = 0;

    @Override
    protected void onJFStart(final IContext context) throws JFException 
    {
        engine = context.getEngine();
        history = context.getHistory();

        context.setSubscribedInstruments(java.util.Collections.singleton(Instrument.EURUSD), true);
        context.setSubscribedInstruments(java.util.Collections.singleton(Instrument.GBPUSD), true);

        lastTickTime = history.getLastTick(Instrument.EURUSD).getTime();
        lastProfitLossInPips_EURUSD = 0;
        lastProfitLossInPips_GBPUSD = 0;
    }

    @Override
    protected void onJFBar(final Instrument instrument, final Period period,
                           final IBar askBar, final IBar bidBar) throws JFException {}

    @Override
    protected void onJFTick(final Instrument instrument, final ITick tick) throws JFException
    {
        //================ Opening First Order EURUSD ===============

        if (engine.getOrders(Instrument.EURUSD).size() == 0)
        {
            String label = getLabel(Instrument.EURUSD);

            final OrderParams EURUSD_BUY = OrderParams
                .forInstrument(Instrument.EURUSD)
                .withOrderCommand(IEngine.OrderCommand.BUY)
                .withAmount(baseAmound)
                .withLabel(label + "First_Buy")
                .build();

            final SubmitParams submitEURUSD_BUY = SubmitParams
                .withOrderParams(EURUSD_BUY)
                .doOnStart(() -> System.out.println("Starting to submit order " + EURUSD_BUY.label()))
                .doOnComplete(() -> System.out.println("Order " + EURUSD_BUY.label() + " was submitted."))
                .retryOnReject(3, 1500L)
                .build();

            final OrderParams EURUSD_SELL = OrderParams
                .forInstrument(Instrument.EURUSD)
                .withOrderCommand(OrderCommand.SELL)
                .withAmount(baseAmound)
                .withLabel(label + "First_Sell")
                .build();

            final SubmitParams submitEURUSD_SELL = SubmitParams
                .withOrderParams(EURUSD_SELL)
                .doOnStart(() -> System.out.println("Starting to submit order " + EURUSD_SELL.label()))
                .doOnComplete(() -> System.out.println("Order " + EURUSD_SELL.label() + " was submitted."))
                .retryOnReject(3, 1500L)
                .build();

            int shift = 1;
            IBar prevBar = history.getBar(Instrument.EURUSD, Period.ONE_HOUR, OfferSide.BID, shift);
            double open = prevBar.getOpen();
            double close = prevBar.getClose();
            double delta = (open - close) / Instrument.EURUSD.getPipValue();
            double startRate = 10.0;

            if (delta < startRate) 
            {
                orderUtil.submitOrder(submitEURUSD_BUY);
                lastProfitLossInPips_EURUSD = 0;
            }
            else
            {
                orderUtil.submitOrder(submitEURUSD_SELL);
                lastProfitLossInPips_EURUSD = 0;
            }
        }

        //================ Opening First Order GBPUSD ===============

        if (engine.getOrders(Instrument.GBPUSD).size() == 0)
        {
            String label = getLabel(Instrument.GBPUSD);

            final OrderParams GBPUSD_BUY = OrderParams
                    .forInstrument(Instrument.GBPUSD)
                    .withOrderCommand(IEngine.OrderCommand.BUY)
                    .withAmount(baseAmound)
                    .withLabel(label + "First_Buy")
                    .build();

            final SubmitParams submitGBPUSD_BUY = SubmitParams
                    .withOrderParams(GBPUSD_BUY)
                    .doOnStart(() -> System.out.println("Starting to submit order " + GBPUSD_BUY.label()))
                    .doOnComplete(() -> System.out.println("Order " + GBPUSD_BUY.label() + " was submitted."))
                    .retryOnReject(3, 1500L)
                    .build();

            final OrderParams GBPUSD_SELL = OrderParams
                    .forInstrument(Instrument.GBPUSD)
                    .withOrderCommand(OrderCommand.SELL)
                    .withAmount(baseAmound)
                    .withLabel(label + "First_Sell")
                    .build();

            final SubmitParams submitGBPUSD_SELL = SubmitParams
                    .withOrderParams(GBPUSD_SELL)
                    .doOnStart(() -> System.out.println("Starting to submit order " + GBPUSD_SELL.label()))
                    .doOnComplete(() -> System.out.println("Order " + GBPUSD_SELL.label() + " was submitted."))
                    .retryOnReject(3, 1500L)
                    .build();

            int shift = 1;  //change open condition to abonacci style
            IBar prevBar = history.getBar(Instrument.GBPUSD, Period.ONE_HOUR, OfferSide.BID, shift);
            double open = prevBar.getOpen();
            double close = prevBar.getClose();
            double delta = (open - close) / Instrument.GBPUSD.getPipValue();
            double startRate = 10.0;

            if (delta < startRate)
            {
                orderUtil.submitOrder(submitGBPUSD_BUY);
                lastProfitLossInPips_GBPUSD = 0;
            }
            else
            {
                orderUtil.submitOrder(submitGBPUSD_SELL);
                lastProfitLossInPips_GBPUSD = 0;
            }
        }

        //============ Trailing EURUSD ===============

        double trailingStopRate_EURUSD = 4.9;  //to optimize by abonacci style

        if ((engine.getOrders(Instrument.EURUSD).size() == 1) &&
                (engine.getOrders(Instrument.EURUSD).get(0).getProfitLossInPips() > trailingStopRate_EURUSD))
        {
            if ((tick.getTime() - lastTickTime > 1000) &&
                    ((engine.getOrders(Instrument.EURUSD).get(0).getProfitLossInPips() - lastProfitLossInPips_EURUSD) >
                            0.9))
            {
                double priceDistance = 0.00039; //to optimize by abonacci style

                if (engine.getOrders(Instrument.EURUSD).get(0).isLong())
                {
                    engine.getOrders(Instrument.EURUSD).get(0).setStopLossPrice(tick.getBid() - priceDistance);
                }
                else if (!engine.getOrders(Instrument.EURUSD).get(0).isLong())
                {
                    engine.getOrders(Instrument.EURUSD).get(0).setStopLossPrice(tick.getAsk() + priceDistance);
                }

                lastTickTime = tick.getTime();
                lastProfitLossInPips_EURUSD = engine.getOrders(Instrument.EURUSD).get(0).getProfitLossInPips();
            }
        }

        //============ Trailing GBPUSD ===============

        double trailingStopRate_GBPUSD = 4.9;  //to optimize by abonacci style

        if ((engine.getOrders(Instrument.GBPUSD).size() == 1) &&
                (engine.getOrders(Instrument.GBPUSD).get(0).getProfitLossInPips() > trailingStopRate_GBPUSD))
        {
            if ((tick.getTime() - lastTickTime > 1000) &&
                    ((engine.getOrders(Instrument.GBPUSD).get(0).getProfitLossInPips() - lastProfitLossInPips_GBPUSD) >
                            0.9))
            {
                double priceDistance = 0.00039; //to optimize by abonacci style

                if (engine.getOrders(Instrument.GBPUSD).get(0).isLong())
                {
                    engine.getOrders(Instrument.GBPUSD).get(0).setStopLossPrice(tick.getBid() - priceDistance);
                }
                else if (!engine.getOrders(Instrument.GBPUSD).get(0).isLong())
                {
                    engine.getOrders(Instrument.GBPUSD).get(0).setStopLossPrice(tick.getAsk() + priceDistance);
                }

                lastTickTime = tick.getTime();
                lastProfitLossInPips_GBPUSD = engine.getOrders(Instrument.GBPUSD).get(0).getProfitLossInPips();
            }
        }

        //================ Averaging EURUSD ================

        double averagingRate_EURUSD = -24.9; //to optimize by abonacci style

        if ((engine.getOrders(Instrument.EURUSD).size() == 1) &&
            (engine.getOrders(Instrument.EURUSD).get(0).getProfitLossInPips() < averagingRate_EURUSD))
        {
            double amount = engine.getOrders(Instrument.EURUSD).get(0).getAmount();

            String label = getLabel(Instrument.EURUSD);

            final OrderParams EURUSD_BUY_A = OrderParams
                    .forInstrument(Instrument.EURUSD)
                    .withOrderCommand(OrderCommand.BUY)
                    .withAmount(amount)
                    .withLabel(label + "A_Buy")
                    .build();

            final SubmitParams submitEURUSD_BUY_A = SubmitParams
                    .withOrderParams(EURUSD_BUY_A)
                    .doOnStart(() -> System.out.println("Starting to submit order " + EURUSD_BUY_A.label()))
                    .doOnComplete(() -> System.out.println("Order " + EURUSD_BUY_A.label() + " was submitted."))
                    .retryOnReject(3, 1500L)
                    .build();

            final OrderParams EURUSD_SELL_A = OrderParams
                    .forInstrument(Instrument.EURUSD)
                    .withOrderCommand(OrderCommand.SELL)
                    .withAmount(amount)
                    .withLabel(label + "A_Sell")
                    .build();

            final SubmitParams submitEURUSD_SELL_A = SubmitParams
                    .withOrderParams(EURUSD_SELL_A)
                    .doOnStart(() -> System.out.println("Starting to submit order " + EURUSD_SELL_A.label()))
                    .doOnComplete(() -> System.out.println("Order " + EURUSD_SELL_A.label() + " was submitted."))
                    .retryOnReject(3, 1500L)
                    .build();

            if (engine.getOrders(Instrument.EURUSD).get(0).isLong())
            {
                orderUtil.submitOrder(submitEURUSD_BUY_A);
            }
            else if (!engine.getOrders(Instrument.EURUSD).get(0).isLong())
            {
                orderUtil.submitOrder(submitEURUSD_SELL_A);
            }
        }

        //================ Averaging GBPUSD ================

        double averagingRate_GBPUSD = -24.9; //to optimize by abonacci style

        if ((engine.getOrders(Instrument.GBPUSD).size() == 1) &&
                (engine.getOrders(Instrument.GBPUSD).get(0).getProfitLossInPips() < averagingRate_GBPUSD))
        {
            double amount = engine.getOrders(Instrument.GBPUSD).get(0).getAmount();

            String label = getLabel(Instrument.GBPUSD);

            final OrderParams GBPUSD_BUY_A = OrderParams
                    .forInstrument(Instrument.GBPUSD)
                    .withOrderCommand(OrderCommand.BUY)
                    .withAmount(amount)
                    .withLabel(label + "A_Buy")
                    .build();

            final SubmitParams submitGBPUSD_BUY_A = SubmitParams
                    .withOrderParams(GBPUSD_BUY_A)
                    .doOnStart(() -> System.out.println("Starting to submit order " + GBPUSD_BUY_A.label()))
                    .doOnComplete(() -> System.out.println("Order " + GBPUSD_BUY_A.label() + " was submitted."))
                    .retryOnReject(3, 1500L)
                    .build();

            final OrderParams GBPUSD_SELL_A = OrderParams
                    .forInstrument(Instrument.GBPUSD)
                    .withOrderCommand(OrderCommand.SELL)
                    .withAmount(amount)
                    .withLabel(label + "A_Sell")
                    .build();

            final SubmitParams submitGBPUSD_SELL_A = SubmitParams
                    .withOrderParams(GBPUSD_SELL_A)
                    .doOnStart(() -> System.out.println("Starting to submit order " + GBPUSD_SELL_A.label()))
                    .doOnComplete(() -> System.out.println("Order " + GBPUSD_SELL_A.label() + " was submitted."))
                    .retryOnReject(3, 1500L)
                    .build();

            if (engine.getOrders(Instrument.GBPUSD).get(0).isLong())
            {
                orderUtil.submitOrder(submitGBPUSD_BUY_A);
            }
            else if (!engine.getOrders(Instrument.GBPUSD).get(0).isLong())
            {
                orderUtil.submitOrder(submitGBPUSD_SELL_A);
            }
        }

        //====================== Merging EURUSD =====================

        if (engine.getOrders(Instrument.EURUSD).size() == 2)
        {
            List<IOrder> mergeableOrders = Arrays.asList(engine.getOrders(Instrument.EURUSD).get(0),
                    engine.getOrders(Instrument.EURUSD).get(1));

            String label = getLabel(Instrument.EURUSD);

            MergeParams merge_buy = new MergeParams
                    .Builder(label + "_Merged_Buy", mergeableOrders)
                    .build();

            MergeParams merge_sell = new MergeParams
                    .Builder(label + "_Merged_Sell", mergeableOrders)
                    .build();

            if ((engine.getOrders(Instrument.EURUSD).get(0).isLong()) &&
                    (engine.getOrders(Instrument.EURUSD).get(1).isLong()))
            {
                orderUtil.mergeOrders(merge_buy);
                lastProfitLossInPips_EURUSD = 0;
            }
            if (!(engine.getOrders(Instrument.EURUSD).get(0).isLong()) &&
                    !(engine.getOrders(Instrument.EURUSD).get(1).isLong()))
            {
                orderUtil.mergeOrders(merge_sell);
                lastProfitLossInPips_EURUSD = 0;
            }
        }

        //====================== Merging GBPUSD =====================

        if (engine.getOrders(Instrument.GBPUSD).size() == 2)
        {
            List<IOrder> mergeableOrders = Arrays.asList(engine.getOrders(Instrument.GBPUSD).get(0),
                    engine.getOrders(Instrument.GBPUSD).get(1));

            String label = getLabel(Instrument.GBPUSD);

            MergeParams merge_buy = new MergeParams
                    .Builder(label + "_Merged_Buy", mergeableOrders)
                    .build();

            MergeParams merge_sell = new MergeParams
                    .Builder(label + "_Merged_Sell", mergeableOrders)
                    .build();

            if ((engine.getOrders(Instrument.GBPUSD).get(0).isLong()) &&
                    (engine.getOrders(Instrument.GBPUSD).get(1).isLong()))
            {
                orderUtil.mergeOrders(merge_buy);
                lastProfitLossInPips_GBPUSD = 0;
            }
            if (!(engine.getOrders(Instrument.GBPUSD).get(0).isLong()) &&
                    !(engine.getOrders(Instrument.GBPUSD).get(1).isLong()))
            {
                orderUtil.mergeOrders(merge_sell);
                lastProfitLossInPips_GBPUSD = 0;
            }
        }
    }

    private String getLabel(Instrument instrument)
    {
        String label = instrument.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }

    @Override
    protected void onJFMessage(final IMessage message) throws JFException {}

    @Override
    protected void onJFStop() throws JFException {}

    @Override
    protected void onJFAccount(final IAccount account) throws JFException {}
}
