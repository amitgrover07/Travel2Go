package com.travel2go.backend.controller;

import com.travel2go.backend.model.GroupBooking;
import com.travel2go.backend.model.TravellerGroup;
import com.travel2go.backend.model.TravellerProfile;
import com.travel2go.backend.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @Value("${group.invite.base-url:https://travel2go.in/join}")
    private String inviteBaseUrl;

    private String currentUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @PostMapping
    public ResponseEntity<TravellerGroup> create(@RequestBody CreateGroupRequest req) {
        try {
            return ResponseEntity.ok(groupService.createGroup(currentUserId(), req.name, req.profiles));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TravellerGroup> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(groupService.getGroup(id, currentUserId()));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/travellers")
    public ResponseEntity<TravellerGroup> addTraveller(@PathVariable String id, @RequestBody TravellerProfile profile) {
        try {
            return ResponseEntity.ok(groupService.addTraveller(id, currentUserId(), profile));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/book")
    public ResponseEntity<GroupBooking> book(@PathVariable String id, @RequestBody BookGroupRequest req) {
        try {
            return ResponseEntity.ok(groupService.bookGroup(id, currentUserId(), req.tripId, req.legIds));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<Map<String, String>> invite(@PathVariable String id) {
        try {
            String token = groupService.createInvite(id, currentUserId());
            return ResponseEntity.ok(Map.of("inviteToken", token, "inviteUrl", inviteBaseUrl + "/" + token));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/join/{inviteToken}")
    public ResponseEntity<TravellerGroup> join(@PathVariable String inviteToken, @RequestBody TravellerProfile profile) {
        try {
            return ResponseEntity.ok(groupService.join(inviteToken, profile));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    public static class CreateGroupRequest { public String name; public List<TravellerProfile> profiles; }
    public static class BookGroupRequest { public String tripId; public List<String> legIds; }
}
