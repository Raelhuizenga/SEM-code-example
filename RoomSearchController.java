package nl.tudelft.sem.roomapp.controller;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import nl.tudelft.sem.roomapp.handlers.AdminValidator;
import nl.tudelft.sem.roomapp.handlers.AuthenticationValidator;
import nl.tudelft.sem.roomapp.handlers.RightsValidator;
import nl.tudelft.sem.roomapp.models.Room;
import nl.tudelft.sem.roomapp.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;


@RestController
@RequestMapping("/")
public class RoomSearchController {

    @Autowired
    private transient RoomRepository roomRepository;
    private transient AdminValidator adminValidator;
    private transient RestTemplate restTemplate = new RestTemplate();

    RoomSearchController() {
        (adminValidator = new AuthenticationValidator(restTemplate))
                .setNext(new RightsValidator());
    }

    /**
     * The end point /search/all_criteria can be used to request
     * a list of rooms that meet certain criteria.
     *
     * @param capacity     the desired capacity of the room
     * @param buildingName the name of the desired building
     * @param startsAt     start time of the desired time slot
     * @param endsAt       end time of the desired time slot
     * @param equipment    desired equipment available in the room
     * @return a list containing the room ids that meet the given criteria
     */
    @GetMapping("/search/all-criteria")
    Map<String, Iterable<Room>> getRoomBySearchCriteria(
            @RequestParam(name = "capacity", required = false)
                    Integer capacity,
            @RequestParam(name = "buildingName", required = false)
                    String buildingName,
            @RequestParam(name = "startsAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime startsAt,
            @RequestParam(name = "endsAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime endsAt,
            @RequestParam(name = "equipment", required = false)
                    List<String> equipment
    ) {
        // Convert Date-time to time.
        // Ignoring PMD below because null assignment on default is essential.
        // This is because start and end needs to be null if startsAt or endsAt is null.
        LocalTime start = null; //NOPMD
        LocalTime end = null; //NOPMD
        // LocalTime Variable start and end need to be initialized in all cases.
        if (startsAt != null) {
            start = LocalTime.from(startsAt);
        }
        if (endsAt != null) {
            end = LocalTime.from(endsAt);
        }

        // Check if we should filter on equipment or not.
        long equipmentListSize;
        if (equipment != null) {
            equipmentListSize = equipment.size();
        } else {
            equipmentListSize = 0;
        }

        List<Room> listRooms = roomRepository.getAllByAllCriteria(capacity, buildingName,
                start, end, equipment, equipmentListSize);

        // When the request contains start and end times, the final part of this
        // mapping checks with the Booking service whether any of the selected rooms
        // have already been booked during the given time period.
        if (startsAt != null && endsAt != null) {
            listRooms = checkBookedRooms(listRooms, start, end);
        }

        Map<String, Iterable<Room>> json = new HashMap<>();
        json.put("rooms", listRooms);

        return json;
    }

    /**
     * Make a call to the Booking service to check whether any of the rooms
     * given already have bookings overlapping with the given time period.
     *
     * @param listRooms the list of rooms that must be checked
     * @param startsAt the start of the requested time period
     * @param endsAt the end of the requested time period
     * @return a list of rooms without any bookings during [startsAt, endsAt]
     */
    private List<Room> checkBookedRooms(List<Room> listRooms,
                                        LocalTime startsAt,
                                        LocalTime endsAt) {
        List<Room> availableRooms = new ArrayList<>();

        // Add the so far selected room IDs to the request to the Booking service
        String roomIds = "";
        for (Room room : listRooms) {
            roomIds += room.getId() + ",";
        }
        if (roomIds.length() > 0) {
            roomIds = roomIds.substring(0, roomIds.length() - 1);
        }
        String filterUrl = "http://localhost:8082/available/multiple/" + roomIds;

        // Add parameters to the request
        Map<String, String> params = new HashMap<>();
        params.put("startsAt", startsAt.toString());
        params.put("endsAt", endsAt.toString());

        // Make call to Booking service endpoint for checking availability
        ResponseEntity<String> response =
                restTemplate.getForEntity(filterUrl, String.class, params);
        String responseIds = response.getBody();

        // Convert the response back to a list of IDs
        int cutOffStart = "{availableRooms=[".length();
        int cutOffEnd = responseIds.length() - 2;
        String textIdList = responseIds.substring(cutOffStart, cutOffEnd);
        List<Integer> idList = new ArrayList<>();
        // PMD does not recognize the Scanner.close() method (6 lines down),
        // and therefore thinks the Scanner remains open
        Scanner scanner = new Scanner(textIdList).useDelimiter(", "); // NOPMD
        while (scanner.hasNext()) {
            idList.add(Integer.valueOf(scanner.next()));
        }
        scanner.close();

        // Retrieve available rooms from database by ID
        for (Integer id : idList) {
            availableRooms.add(roomRepository.findRoomById(id));
        }
        return availableRooms;
    }
}