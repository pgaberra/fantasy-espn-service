package com.fantasy.espn.player;

import com.fantasy.espn.player.dto.GoalieResponse;
import com.fantasy.espn.player.dto.PlayerStatsResponse;
import com.fantasy.espn.player.dto.PlayerSyncStatusResponse;
import com.fantasy.espn.player.dto.SyncAcceptedResponse;
import com.fantasy.espn.player.dto.SkaterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "When the cached pool was last refreshed, and how many players it holds",
            description = "For callers that only need to know whether the pool has moved, so "
                    + "they do not have to fetch the pool to find out.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Sync status returned"))
    @GetMapping("/sync/latest")
    public PlayerSyncStatusResponse lastSync() {
        return playerService.lastSync();
    }

    @Operation(summary = "Refresh the cached player read model from ESPN",
            description = "Runs asynchronously and answers as soon as it is under way: the whole "
                    + "player universe is fetched, parsed and checked against the image CDN, "
                    + "which takes minutes. Watch it with GET /api/v1/espn/players/sync/latest "
                    + "until running is false and syncedAt has moved.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Sync started"),
            @ApiResponse(responseCode = "409", description = "A sync is already running")
    })
    @PostMapping("/sync")
    public ResponseEntity<SyncAcceptedResponse> sync() {
        // startAsync is the one place the claim is made, so two callers at once cannot both
        // be told they started it.
        if (!syncService.startAsync()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new SyncAcceptedResponse("running"));
        }
        return ResponseEntity.accepted().body(new SyncAcceptedResponse("started"));
    }
}
