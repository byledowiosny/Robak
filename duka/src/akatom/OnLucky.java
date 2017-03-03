package akatom;

/**
 * Created by jerzy on 27/02/17.
 */
import java.util.Arrays;
import java.util.List;
//import java.util.ArrayList;
//import java.util.Optional;
//import java.awt.Color;
//import java.text.DecimalFormat;

//import java.util.function.Predicate;
//import java.util.function.Consumer;
//import java.util.function.Function;
//import java.util.function.Supplier;
//import java.util.function.UnaryOperator;
//import java.util.function.BinaryOperator;

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
//import com.jforex.programming.order.task.params.SetSLTPMode;
//import com.jforex.programming.order.task.params.basic.SetSLParams;
import com.jforex.programming.order.task.params.basic.MergeParams;
import com.jforex.programming.order.task.params.basic.SubmitParams;
import com.jforex.programming.strategy.JForexUtilsStrategy;

@RequiresFullAccess
public class OnLucky extends JForexUtilsStrategy 
{
    private IEngine engine;
    private IHistory history;

    @Configurable("Instrument")
    public Instrument instrument = Instrument.EURUSD;

    private long lastTickTime = 0;
    private double lastProfitLossInPips = 0;

    @Override
    protected void onJFStart(final IContext context) throws JFException 
    {
        engine= context.getEngine();
        history = context.getHistory();
        context.setSubscribedInstruments(java.util.Collections.singleton(instrument), true);

        lastTickTime = history.getLastTick(instrument).getTime();
    }

    @Override
    protected void onJFBar(final Instrument instrument, final Period period,
                           final IBar askBar, final IBar bidBar) throws JFException {}

    @Override
    protected void onJFTick(final Instrument instrument, final ITick tick) throws JFException 
    {
        //================Opening First Order===============

        if (engine.getOrders().size() == 0)
        {
            final OrderParams EURUSD_BUY = OrderParams
                .forInstrument(Instrument.EURUSD)
                .withOrderCommand(IEngine.OrderCommand.BUY)
                .withAmount(0.01)
                .withLabel("First_Buy")
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
                .withAmount(0.01)
                .withLabel("First_Sell")
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
            double delta = (open - close) / instrument.getPipValue();
            double startRate = 10.0;

            if (delta < startRate) 
            {
                orderUtil.submitOrder(submitEURUSD_BUY);
            }
            else
            {
                orderUtil.submitOrder(submitEURUSD_SELL);
            }
        }

        //============Trailing===============

        double trailingStopRate = 4.9;  //optimize by abonacci style
        double priceDistance = 0.00039; //idem

        if ((engine.getOrders().size() == 1) &&
                (engine.getOrders().get(0).getProfitLossInPips() > trailingStopRate))
        {
            if ((tick.getTime() - lastTickTime > 1000) &&
                    ((engine.getOrders().get(0).getProfitLossInPips() - lastProfitLossInPips) > 0.9))
            {
                if (engine.getOrders().get(0).isLong())
                {
                    engine.getOrders().get(0).setStopLossPrice(tick.getBid() - priceDistance);
                }
                else
                {
                    engine.getOrders().get(0).setStopLossPrice(tick.getAsk() + priceDistance);
                }

                lastTickTime = tick.getTime();
                lastProfitLossInPips = engine.getOrders().get(0).getProfitLossInPips();
            }
        }

        //================Averaging=================

        double averagingRate = -24.9; //optimize by abonacci style

        if ((engine.getOrders().size() == 1) &&
            (engine.getOrders().get(0).getProfitLossInPips() < averagingRate))
        {
            double amount = engine.getOrders().get(0).getAmount();
            double multiplier = 2.0;

            final OrderParams EURUSD_BUY_A = OrderParams
                    .forInstrument(Instrument.EURUSD)
                    .withOrderCommand(OrderCommand.BUY)
                    .withAmount(amount * multiplier)
                    .withLabel("A_Buy")
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
                    .withAmount(amount * multiplier)
                    .withLabel("A_Sell")
                    .build();

            final SubmitParams submitEURUSD_SELL_A = SubmitParams
                    .withOrderParams(EURUSD_SELL_A)
                    .doOnStart(() -> System.out.println("Starting to submit order " + EURUSD_SELL_A.label()))
                    .doOnComplete(() -> System.out.println("Order " + EURUSD_SELL_A.label() + " was submitted."))
                    .retryOnReject(3, 1500L)
                    .build();

            if (engine.getOrders().get(0).isLong())
            {
                orderUtil.submitOrder(submitEURUSD_BUY_A);
            }
            else
            {
                orderUtil.submitOrder(submitEURUSD_SELL_A);
            }
        }

        //======================Merging=====================

        if (engine.getOrders().size() == 2)
        {
            List<IOrder> mergeableOrders = Arrays.asList(engine.getOrders().get(0), engine.getOrders().get(1));

            MergeParams merge_buy = new MergeParams
                    .Builder("Merged_Buy", mergeableOrders)
                    .build();

            MergeParams merge_sell = new MergeParams
                    .Builder("Merged_Sell", mergeableOrders)
                    .build();

            if ((engine.getOrders().get(0).isLong()) && (engine.getOrders().get(1).isLong()))
            {
                orderUtil.mergeOrders(merge_buy);
            }
            if (!(engine.getOrders().get(0).isLong()) && !(engine.getOrders().get(1).isLong()))
            {
                orderUtil.mergeOrders(merge_sell);
            }
        }
    }

    @Override
    protected void onJFMessage(final IMessage message) throws JFException {}

    @Override
    protected void onJFStop() throws JFException {}

    @Override
    protected void onJFAccount(final IAccount account) throws JFException {}
}
