package nl.tudelft.sem.roomapp;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.tudelft.sem.roomapp.models.Building;
import nl.tudelft.sem.roomapp.models.Equipment;
import nl.tudelft.sem.roomapp.models.Room;
import nl.tudelft.sem.roomapp.repository.BuildingRepository;
import nl.tudelft.sem.roomapp.repository.EquipmentRepository;
import nl.tudelft.sem.roomapp.repository.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;



@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RoomControllerTest {

    @Autowired
    transient MockMvc mvc;

    // Create mocked servers for the user and room service
    private final transient WireMockServer bookingMockServer = new WireMockServer(8082);

    @Autowired
    transient RoomRepository roomRepository;

    @Autowired
    transient BuildingRepository buildingRepository;

    @Autowired
    transient EquipmentRepository equipmentRepository;

    transient String ewi = "ewi"; //Anti PMD string
    transient String room1 = "Room1"; //Anti PMD string
    transient String room3 = "Room3"; //Anti PMD string
    transient String computer = "Computer"; //Anti PMD string
    transient String searchallcriteria = "/search/all-criteria"; //Anti PMD string
    transient String capacity = "capacity"; //Anti PMD string
    transient String roomsname0 = "$.rooms[0].name"; //Anti PMD string
    transient String roomsname1 = "$.rooms[1].name"; //Anti PMD string
    transient String availableRooms = "availableRooms"; //Anti PMD string
    transient String url = "/available/multiple/"; //Anti PMD string
    transient String startsAtString = "startsAt"; //Anti PMD string
    transient String endsAtString = "endsAt"; //Anti PMD string
    transient String pulse = "pulse"; //Anti PMD string
    transient String whiteboard = "Whiteboard"; //Anti PMD string

    /**
     * Initialization of the databases containing one room.
     */
    @BeforeEach
    public void cleanDatabases() {
        equipmentRepository.deleteAll();
        roomRepository.deleteAll();
        buildingRepository.deleteAll();

        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(19, 0);
        Building building = new Building(ewi, start, end);
        buildingRepository.saveAndFlush(building);
        Room room = new Room(room1, 10, building);
        roomRepository.saveAndFlush(room);
        Equipment equipment = new Equipment(room, computer);
        equipmentRepository.saveAndFlush(equipment);
    }

    @BeforeEach
    void initMockServer() {
        bookingMockServer.resetAll();
        bookingMockServer.start();
    }

    @AfterEach
    void closeMockServer() {
        bookingMockServer.stop();
    }

    @Order(1)
    @Test
    public void testBookingServiceStub() throws Exception {
        // Mock a valid response from the Booking service
        Map<String, Iterable<Integer>> body = new HashMap<>();
        List<Integer> ids = new ArrayList<>();
        int idRoom1 = roomRepository.getAllByCapacity(10).get(0).getId();
        ids.add(idRoom1);
        body.put(availableRooms, ids);
        createStubJson(8082, url + idRoom1, body);

        LocalDateTime startsAt = LocalDateTime.of(2021, 12, 1,
                9, 20, 0);
        LocalDateTime endsAt = LocalDateTime.of(2021, 12, 1,
                9, 40, 0);

        // The Room service should send a request to the stub to filter Room1,
        // and it should get a response from the stub containing the ID of Room1.
        this.mvc.perform(get(searchallcriteria)
                        .param(startsAtString, startsAt.toString())
                        .param(endsAtString, endsAt.toString())
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room1));

    }

    @Order(2)
    @Test
    public void getMappingGetRoomBySearchCriteriaTestCapacity() throws Exception {
        List<Room> emptyList = new ArrayList<>();
        this.mvc.perform(get(searchallcriteria)
                        .param(capacity, "20"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rooms").value(emptyList));
    }

    @Order(3)
    @Test
    public void getMappingGetRoomBySearchCriteriaTestNoCriteria() throws Exception {
        this.mvc.perform(get(searchallcriteria))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room1));
    }

    @Order(4)
    @Test
    public void getMappingGetRoomBySearchCriteriaAllTrue() throws Exception {
        // Mock a valid response from the Booking service
        Map<String, Iterable<Integer>> body = new HashMap<>();
        List<Integer> ids = new ArrayList<>();
        int idRoom1 = roomRepository.getAllByCapacity(10).get(0).getId();
        ids.add(idRoom1);
        body.put(availableRooms, ids);
        createStubJson(8082, url + idRoom1, body);

        LocalDateTime startsAt = LocalDateTime.of(2021, 12, 1,
                10, 20, 0);
        LocalDateTime endsAt = LocalDateTime.of(2021, 12, 1,
                11, 40, 0);

        this.mvc.perform(get(searchallcriteria)
                        .param(capacity, "0")
                        .param("buildingName", ewi)
                        .param("startsAt", startsAt.toString())
                        .param("endsAt", endsAt.toString())
                        .param("equipment", computer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room1));
    }

    @Order(5)
    @Test
    public void getMappingGetRoomBySearchCriteriaAllTrueTwoRooms() throws Exception {
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(19, 0);
        Building building = new Building(ewi, start, end);
        buildingRepository.saveAndFlush(building);
        Room room = new Room("Room2", 5, building);
        roomRepository.saveAndFlush(room);
        Equipment equipment = new Equipment(room, computer);
        equipmentRepository.saveAndFlush(equipment);

        // Mock a valid response from the Booking service
        Map<String, Iterable<Integer>> body = new HashMap<>();
        List<Integer> ids = new ArrayList<>();
        int idRoom1 = roomRepository.getAllByCapacity(10).get(0).getId();
        int idRoom2 = idRoom1 + 1;
        ids.add(idRoom1);
        ids.add(idRoom2);
        body.put(availableRooms, ids);
        createStubJson(8082, url + idRoom1 + "," + idRoom2, body);

        LocalDateTime startsAt = LocalDateTime.of(2021, 12, 1,
                10, 20, 0);
        LocalDateTime endsAt = LocalDateTime.of(2021, 12, 1,
                11, 40, 0);

        this.mvc.perform(get(searchallcriteria)
                        .param(capacity, "0")
                        .param("buildingName", ewi)
                        .param("startsAt", startsAt.toString())
                        .param("endsAt", endsAt.toString())
                        .param("equipment", computer))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room1))
                .andExpect(jsonPath(roomsname1).value("Room2"));
    }

    @Order(6)
    @Test
    public void getMappingGetRoomBySearchCriteriaOneTrueTwoRoomsCapacity() throws Exception {
        this.mvc.perform(get(searchallcriteria)
                        .param(capacity, "7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room1))
                .andExpect(jsonPath(roomsname1).doesNotHaveJsonPath());
    }

    @Order(7)
    @Test
    public void getMappingGetRoomBySearchCriteriaOneTrueThreeRoomsBuilding() throws Exception {
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(10, 0);
        Building building = new Building(pulse, start, end);
        buildingRepository.saveAndFlush(building);
        Room room = new Room(room3, 4, building);
        roomRepository.saveAndFlush(room);
        Equipment equipment = new Equipment(room, whiteboard);
        equipmentRepository.saveAndFlush(equipment);

        this.mvc.perform(get(searchallcriteria)
                        .param("buildingName", "pulse"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room3))
                .andExpect(jsonPath(roomsname1).doesNotHaveJsonPath());
    }

    @Order(8)
    @Test
    public void getMappingGetRoomBySearchCriteriaOneTrueThreeRoomsTime() throws Exception {
        // Add rooms to the database
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(20, 0);
        Building building = new Building(pulse, start, end);
        buildingRepository.saveAndFlush(building);

        Room room2 = new Room("room2", 4, building);
        roomRepository.saveAndFlush(room2);
        Equipment equipment = new Equipment(room2, whiteboard);
        equipmentRepository.saveAndFlush(equipment);

        Room room = new Room(room3, 4, building);
        roomRepository.saveAndFlush(room);
        Equipment equipment3 = new Equipment(room, whiteboard);
        equipmentRepository.saveAndFlush(equipment3);

        // Mock a valid response from the Booking service
        Map<String, Iterable<Integer>> body = new HashMap<>();
        List<Integer> ids = new ArrayList<>();
        int idRoom1 = roomRepository.getAllByCapacity(10).get(0).getId();
        ids.add(idRoom1);
        body.put(availableRooms, ids);
        createStubJson(8082, url + idRoom1, body);

        LocalDateTime startsAt = LocalDateTime.of(2021, 12, 1,
                9, 20, 0);
        LocalDateTime endsAt = LocalDateTime.of(2021, 12, 1,
                9, 40, 0);

        this.mvc.perform(get(searchallcriteria)
                        .param(startsAtString, startsAt.toString())
                        .param(endsAtString, endsAt.toString())
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room1))
                .andExpect(jsonPath(roomsname1).doesNotHaveJsonPath());
    }

    @Order(9)
    @Test
    public void getMappingGetRoomBySearchCriteriaOneTrueThreeRoomsEquipment() throws Exception {
        // Add room to the database
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(20, 0);
        Building building = new Building(pulse, start, end);
        buildingRepository.saveAndFlush(building);
        Room room = new Room(room3, 4, building);
        roomRepository.saveAndFlush(room);
        Equipment equipment3 = new Equipment(room, whiteboard);
        equipmentRepository.saveAndFlush(equipment3);

        this.mvc.perform(get(searchallcriteria)
                        .param("equipment", "Whiteboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath(roomsname0).value(room3))
                .andExpect(jsonPath(roomsname1).doesNotHaveJsonPath());
    }

    /**
     * Create a stub at given port for given url. Will return the given body as JSON.
     *
     * @param port   The port of the simulated endpoint
     * @param url    The url of the simulated endpoint
     * @param body   The JSON that will be returned to the user
     */
    public static void createStubJson(int port, String url, Map<String, Iterable<Integer>> body) {
        configureFor(port);
        stubFor(
                WireMock.get(
                        urlEqualTo(url))
                        .willReturn(
                                okJson(body.toString())
                        )
        );
    }
}
