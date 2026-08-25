package com.fantasy.espn.player;

import com.fantasy.espn.player.dto.GoalieResponse;
import com.fantasy.espn.player.dto.PlayerStatsResponse;
import com.fantasy.espn.player.dto.PlayerSyncResponse;
import com.fantasy.espn.player.dto.SkaterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "ESPN Players",
        description = "Cached player read model: the pool as ESPN lists it now, with a chosen "
                + "season's stats")
@RestController
@RequestMapping("/api/v1/espn/players")
public class EspnPlayerController {

    private static final String SEASON_DESCRIPTION =
            "Season start year — 2025 is the 2025-26 season. Required: the season being collected "
                    + "and the season a caller wants to show are deliberately different for most "
                    + "of the year, so there is no sensible default. A player with no line for it "
                    + "comes back with no stats rather than being left out.";

    private final EspnPlayerService playerService;
    private final EspnPlayerSyncService syncService;

    public EspnPlayerController(EspnPlayerService playerService, EspnPlayerSyncService syncService) {
        this.playerService = playerService;
        this.syncService = syncService;
    }

    @Operation(summary = "All skaters with eligible positions and a season's stats")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Skaters returned"))
    @GetMapping("/skaters")
    public List<SkaterResponse> skaters(
            @Parameter(description = SEASON_DESCRIPTION) @RequestParam int season) {
        return playerService.getSkaters(season);
    }

    @Operation(summary = "All goalies with eligible positions and a season's stats")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Goalies returned"))
    @GetMapping("/goalies")
    public List<GoalieResponse> goalies(
            @Parameter(description = SEASON_DESCRIPTION) @RequestParam int season) {
        return playerService.getGoalies(season);
    }

    @Operation(summary = "Reference-season stat lines for the stats Yahoo does not report "
            + "(hat tricks, shifts, goalie OT losses, time on ice)")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Stat lines returned"))
    @GetMapping
    public PlayerStatsResponse players() {
        return playerService.players();
    }

    @Operation(summary = "Refresh the cached player read model from ESPN")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sync completed"),
            @ApiResponse(responseCode = "502", description = "ESPN was unreachable or returned nothing")
    })
    @PostMapping("/sync")
    public PlayerSyncResponse sync() {
        return syncService.sync();
    }
}
