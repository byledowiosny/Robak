package akatom;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
    private IConsole console;
    private IContext context;
    private IAccount account;

    private int counter = 0;
    long lastTickTime = 0;

    public Set<Instrument> instruments = new HashSet<Instrument>() 
    {{
        add(Instrument.EURUSD);
        add(Instrument.GBPUSD);
        add(Instrument.EURJPY);
        //add(Instrument.GBPCHF);
        //add(Instrument.XAUUSD);
        //add(Instrument.XAGUSD);
    }};
    
    // minimum order amount is 1000 units, except for XAUUSD and XAGUSD which is 1 and 50 respectively
    private double getMinAmount(Instrument instrument)
    {
        switch (instrument)
        {
            case XAUUSD : return 0.000001;
            case XAGUSD : return 0.00005;
            default : return 0.001;
        }
    }

    private double getPriceDistance(Instrument instrument)
    {
        switch (instrument)
        {
            //case XAUUSD : return 0.000001;
            //case XAGUSD : return 0.00005;
            case EURJPY : return 0.039;
            default : return 0.00039;
        }
    }

    private double getPriceStep(Instrument instrument)
    {
        switch (instrument)
        {
            //case XAUUSD : return 0.000001;
            //case XAGUSD : return 0.00005;
            //case GBPCHF : return 0.029;
            case EURJPY : return 0.009;
            default : return 0.00009;
        }
    }

    private double getTrailingStopRate(Instrument instrument)
    {
        switch (instrument)
        {
            //case XAUUSD : return 0.000001;
            //case XAGUSD : return 0.00005;
            //case GBPCHF : return 6.9;
            default : return 4.9;
        }
    }
    
    private void subscribeToInstruments(Set<Instrument> instruments)
    {
        context.setSubscribedInstruments(instruments);
        // wait 1 second for the instruments to get subscribed
        int i = 10;
        while (!context.getSubscribedInstruments().containsAll(instruments)) 
        {
            try 
            {
                Thread.sleep(100);
            } 
            catch (InterruptedException e) 
            {
                console.getOut().println(e.getMessage());
            }
            i--;
        }
    }

    private void trailingOrders() throws JFException
    {
        for (Iterator<Instrument> it = instruments.iterator(); it.hasNext();)
        {
            Instrument inst = it.next();

            if ((engine.getOrders(inst).size() == 1) &&
                    (engine.getOrders(inst).get(0).getProfitLossInPips() > getTrailingStopRate(inst)))
            {
                //we can't update SL or TP more frequently than once per second
                if(history.getLastTick(inst).getTime() - lastTickTime < 1000) {return;}
                double lastBidPrice = history.getLastTick(inst).getBid();
                double lastAskPrice = history.getLastTick(inst).getAsk();
                if (engine.getOrders(inst).get(0).isLong())
                {
                    if ((lastBidPrice - engine.getOrders(inst).get(0).getStopLossPrice()) > (getPriceDistance(inst) +
                            getPriceStep(inst)))
                    {
                        engine.getOrders(inst).get(0).setStopLossPrice(lastBidPrice - getPriceDistance(inst));
                        lastTickTime = history.getLastTick(inst).getTime();
                    }
                }
                else if (!engine.getOrders(inst).get(0).isLong())
                {
                    if ((Math.abs(lastAskPrice - engine.getOrders(inst).get(0).getStopLossPrice())) > (getPriceDistance(inst) +
                            getPriceStep(inst)))
                    {
                        engine.getOrders(inst).get(0).setStopLossPrice(lastAskPrice + getPriceDistance(inst));
                        lastTickTime = history.getLastTick(inst).getTime();
                    }
                }
            }
        }
    }

    @Override
    protected void onJFStart(final IContext context) throws JFException
    {
        engine = context.getEngine();
        console = context.getConsole();
        this.context = context;
        history = context.getHistory();

        // make sure that the instruments have been subscribed
        subscribeToInstruments(instruments);
        for (Iterator<Instrument> it = instruments.iterator(); it.hasNext();)
        {
            Instrument inst = it.next();
            lastTickTime = history.getLastTick(inst).getTime();
        }
    }

    @Override
    protected void onJFTick(final Instrument instrument, final ITick tick) throws JFException
    {
        //====== Trailing Orders if there is no Suitable Leverage =======

        double useOfLeverage = account.getUseOfLeverage();
        while (useOfLeverage > 50)
        {
            trailingOrders();
        }

        //===================== Opening First Order =====================

        for (Iterator<Instrument> it = instruments.iterator(); it.hasNext();)
        {
            Instrument inst = it.next();
            String label = inst.name() + (counter++);

            if (engine.getOrders(inst).size() == 0)
            {
                final OrderParams BUY = OrderParams
                        .forInstrument(inst)
                        .withOrderCommand(IEngine.OrderCommand.BUY)
                        .withAmount(getMinAmount(inst))
                        .withLabel(label + "FirstBuy")
                        .build();

                final SubmitParams submitBUY = SubmitParams
                        .withOrderParams(BUY)
                        .doOnStart(() -> System.out.println("Starting to submit order " + BUY.label()))
                        .doOnComplete(() -> System.out.println("Order " + BUY.label() + " was submitted."))
                        .retryOnReject(3, 1500L)
                        .build();

                final OrderParams SELL = OrderParams
                        .forInstrument(inst)
                        .withOrderCommand(OrderCommand.SELL)
                        .withAmount(getMinAmount(inst))
                        .withLabel(label + "FirstSell")
                        .build();

                final SubmitParams submitSELL = SubmitParams
                        .withOrderParams(SELL)
                        .doOnStart(() -> System.out.println("Starting to submit order " + SELL.label()))
                        .doOnComplete(() -> System.out.println("Order " + SELL.label() + " was submitted."))
                        .retryOnReject(3, 1500L)
                        .build();

                int shift = 1;
                IBar prevBar = history.getBar(inst, Period.ONE_HOUR, OfferSide.BID, shift);
                double open = prevBar.getOpen();
                double close = prevBar.getClose();
                double delta = (open - close) / inst.getPipValue();
                double startRate = 10.0;

                if (delta < startRate)
                {
                    orderUtil.submitOrder(submitBUY);
                }
                else
                {
                    orderUtil.submitOrder(submitSELL);
                }
            }
        }

        //================ Averaging Orders ================

        for (Iterator<Instrument> it = instruments.iterator(); it.hasNext();)
        {
            Instrument inst = it.next();
            String label = inst.name() + (counter++);
            double averagingRate = -24.9;

            if ((engine.getOrders(inst).size() == 1) &&
                    (engine.getOrders(inst).get(0).getProfitLossInPips() < averagingRate))
            {
                double amount = engine.getOrders(inst).get(0).getAmount();

                final OrderParams BUYA = OrderParams
                        .forInstrument(inst)
                        .withOrderCommand(OrderCommand.BUY)
                        .withAmount(amount)
                        .withLabel(label + "ABuy")
                        .build();

                final SubmitParams submitBUYA = SubmitParams
                        .withOrderParams(BUYA)
                        .doOnStart(() -> System.out.println("Starting to submit order " + BUYA.label()))
                        .doOnComplete(() -> System.out.println("Order " + BUYA.label() + " was submitted."))
                        .retryOnReject(3, 1500L)
                        .build();

                final OrderParams SELLA = OrderParams
                        .forInstrument(inst)
                        .withOrderCommand(OrderCommand.SELL)
                        .withAmount(amount)
                        .withLabel(label + "ASell")
                        .build();

                final SubmitParams submitSELLA = SubmitParams
                        .withOrderParams(SELLA)
                        .doOnStart(() -> System.out.println("Starting to submit order " + SELLA.label()))
                        .doOnComplete(() -> System.out.println("Order " + SELLA.label() + " was submitted."))
                        .retryOnReject(3, 1500L)
                        .build();

                if (engine.getOrders(inst).get(0).isLong())
                {
                    orderUtil.submitOrder(submitBUYA);
                }
                else if (!engine.getOrders(inst).get(0).isLong())
                {
                    orderUtil.submitOrder(submitSELLA);
                }
            }
        }

        //====================== Merging Orders =====================

        for (Iterator<Instrument> it = instruments.iterator(); it.hasNext();)
        {
            Instrument inst = it.next();
            String label = inst.name() + (counter++);

            if (engine.getOrders(inst).size() == 2)
            {
                List<IOrder> mergeableOrders =
                        Arrays.asList(engine.getOrders(inst).get(0), engine.getOrders(inst).get(1));

                MergeParams merge_buy = new MergeParams
                        .Builder(label + "MergedBuy", mergeableOrders)
                        .build();

                MergeParams merge_sell = new MergeParams
                        .Builder(label + "MergedSell", mergeableOrders)
                        .build();

                if ((engine.getOrders(inst).get(0).isLong()) && (engine.getOrders(inst).get(1).isLong()))
                {
                    orderUtil.mergeOrders(merge_buy);
                }
                if (!(engine.getOrders(inst).get(0).isLong()) && !(engine.getOrders(inst).get(1).isLong()))
                {
                    orderUtil.mergeOrders(merge_sell);
                }
            }
        }

        //============ Trailing Orders ===============

        for (Iterator<Instrument> it = instruments.iterator(); it.hasNext();)
        {
            Instrument inst = it.next();

            if ((engine.getOrders(inst).size() == 1) &&
                    (engine.getOrders(inst).get(0).getProfitLossInPips() > getTrailingStopRate(inst)))
            {
                //we can't update SL or TP more frequently than once per second
                if(history.getLastTick(inst).getTime() - lastTickTime < 1000) {return;}
                double lastBidPrice = history.getLastTick(inst).getBid();
                double lastAskPrice = history.getLastTick(inst).getAsk();
                if (engine.getOrders(inst).get(0).isLong())
                {
                    if ((lastBidPrice - engine.getOrders(inst).get(0).getStopLossPrice()) > (getPriceDistance(inst) +
                            getPriceStep(inst)))
                    {
                        engine.getOrders(inst).get(0).setStopLossPrice(lastBidPrice - getPriceDistance(inst));
                        lastTickTime = history.getLastTick(inst).getTime();
                    }
                }
                else if (!engine.getOrders(inst).get(0).isLong())
                {
                    if ((Math.abs(lastAskPrice - engine.getOrders(inst).get(0).getStopLossPrice())) > (getPriceDistance(inst) +
                            getPriceStep(inst)))
                    {
                        engine.getOrders(inst).get(0).setStopLossPrice(lastAskPrice + getPriceDistance(inst));
                        lastTickTime = history.getLastTick(inst).getTime();
                    }
                }
            }
        }
    }

    @Override
    protected void onJFBar(final Instrument instrument, final Period period,
        final IBar askBar, final IBar bidBar) throws JFException
    {}

    @Override
    protected void onJFMessage(final IMessage message) throws JFException
    {}

    @Override
    protected void onJFAccount(final IAccount account) throws JFException
    {
        this.account = account;
    }

    @Override
    protected void onJFStop() throws JFException
    {}
}
