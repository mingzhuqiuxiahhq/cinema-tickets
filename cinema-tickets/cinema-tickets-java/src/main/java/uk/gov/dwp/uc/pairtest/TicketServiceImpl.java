package uk.gov.dwp.uc.pairtest;

import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationServiceImpl;

import java.util.*;

public class TicketServiceImpl implements TicketService {

    private TicketPaymentServiceImpl paymentService = new TicketPaymentServiceImpl();
    private SeatReservationServiceImpl seatService = new SeatReservationServiceImpl();
    private static Map<TicketTypeRequest.Type, Integer> ticketPriceMap;

    static {

        ticketPriceMap = new HashMap<>();

        // decouple from ticket type request
        for(TicketTypeRequest.Type val: TicketTypeRequest.Type.values()){
            switch (val) {
                case INFANT ->ticketPriceMap.put(val, 0);
                case CHILD -> ticketPriceMap.put(val, 10);
                case ADULT -> ticketPriceMap.put(val, 20);
                default -> ticketPriceMap.put(val, null);
            }
        }
    }

    private boolean isAccountValid(Long accountId){
        return accountId != null && accountId > 0.0;
    }

    private boolean isAdultTicketValid(int num){
        return num >= 1;
    }

    private boolean isTotalTicketValid(int num){
        return num <=20;
    }

    private boolean isInfantTicketValid(int infant, int adult){
        return adult >= infant;
    }

    private void getPriceAndQuantity(Map<TicketTypeRequest.Type, List<Integer>> tiMap, TicketTypeRequest request){
        // validate input values
        if(request.getNoOfTickets() <= 0 || request.getTicketType() == null){
            throw new InvalidPurchaseException("Invalid number of tickets or ticket type.");
        }

        // retrieve ticket price and quantity
        int totalPrice = request.getNoOfTickets() * ticketPriceMap.get(request.getTicketType());
        List<Integer> ticketList = new LinkedList<>();

        // update ticket price and quantity if exists
        if(tiMap.containsKey(request.getTicketType())){
            int totalQuantity = tiMap.get(request.getTicketType()).get(0) + request.getNoOfTickets();
            totalPrice += tiMap.get(request.getTicketType()).get(1);
            ticketList.add(totalQuantity);
            ticketList.add(totalPrice);
            tiMap.put(request.getTicketType(), ticketList);
        } else {
            ticketList.add(request.getNoOfTickets());
            ticketList.add(totalPrice);
            tiMap.put(request.getTicketType(), tiMap.getOrDefault(request.getTicketType(), ticketList));
        }
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        // validate account id
        if(!isAccountValid(accountId)){
            throw new InvalidPurchaseException("Invalid account number");
        }


        Map<TicketTypeRequest.Type, List<Integer>> ticketInfoMap = new HashMap<>();

        // loop the ticket request array
        for(TicketTypeRequest ttr:  ticketTypeRequests){
            // get ticket type and price
            getPriceAndQuantity(ticketInfoMap, ttr);
        }

        int totalTicket = 0;
        int totalPrice = 0;
        int adultTicket = 0;
        int infantTicket = 0;
        for(Map.Entry<TicketTypeRequest.Type, List<Integer>> entry: ticketInfoMap.entrySet()){
            totalTicket += entry.getValue().get(0);
            if(String.valueOf(entry.getKey()).equalsIgnoreCase("INFANT")){
                infantTicket += entry.getValue().get(0);
                continue;
            }
            totalPrice += entry.getValue().get(1);
            if(String.valueOf(entry.getKey()).equalsIgnoreCase("ADULT")){
                adultTicket += entry.getValue().get(0);
            }
        }

        if(!isTotalTicketValid(totalTicket)){
            throw new InvalidPurchaseException("Maximum of 20 tickets per purchase.");
        }
        if(!isAdultTicketValid(adultTicket)){
            throw new InvalidPurchaseException("Must purchase at least 1 adult ticket.");
        }
        if(!isInfantTicketValid(infantTicket, adultTicket)){
            throw new InvalidPurchaseException("Infant ticket must not be more than adult tickets.");
        }

        paymentService.makePayment(accountId, totalPrice);
        seatService.reserveSeat(accountId, totalTicket);

    }
}
