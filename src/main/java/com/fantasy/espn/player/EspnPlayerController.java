package com.fantasy.espn.player;

import com.fantasy.espn.player.dto.PlayerStatsResponse;
import com.fantasy.espn.player.dto.PlayerSyncResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ESPN Players", description = "Cached ESPN season stat lines for stats Yahoo does not report")
@RestController
@RequestMapping("/api/v1/espn/players")
public class EspnPlayerController {

    private final EspnPlayerStatsService playerStatsService;

    public EspnPlayerController(EspnPlayerStatsService playerStatsService) {
        this.playerStatsService = playerStatsService;
    }

    @Operation(summary = "Every cached ESPN stat line (hat tricks, shifts, goalie OT losses, time on ice)")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Stat lines returned"))
    @GetMapping
    public PlayerStatsResponse players() {
        return playerStatsService.players();
    }

    @Operation(summary = "Refresh the cached stat lines from ESPN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sync completed"),
            @ApiResponse(responseCode = "502", description = "ESPN was unreachable or returned nothing")
    })
    @PostMapping("/sync")
    public PlayerSyncResponse sync() {
        return playerStatsService.sync();
    }
}
