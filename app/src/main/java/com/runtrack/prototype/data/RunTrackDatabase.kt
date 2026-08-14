package com.runtrack.prototype.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "workouts",
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["type"]),
        Index(value = ["status"]),
        Index(value = ["status", "startedAt"]),
        Index(value = ["status", "type", "startedAt"]),
        Index(value = ["sessionToken"], unique = true),
    ],
)
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val sessionToken: String,
    val type: String,
    val goalKind: String,
    val goalDistanceMeters: Double?,
    val goalDurationMillis: Long?,
    val goalReachedAt: Long?,
    val status: String,
    val startedAt: Long,
    val startedElapsedRealtimeMillis: Long,
    val endedAt: Long?,
    val elapsedMillis: Long,
    val movingMillis: Long,
    val distanceMeters: Double,
    val averageSpeedMps: Double,
    val caloriesEstimate: Int,
    val heartRateAverageBpm: Int?,
    val heartRateMaxBpm: Int?,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val title: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        // Wall clock may move backwards; uniqueness is anchored to monotonic time inside a route segment.
        Index(value = ["workoutId", "segmentIndex", "elapsedRealtimeMillis"], unique = true),
        Index(value = ["workoutId", "timestampMillis"]),
        Index(value = ["workoutId"]),
    ],
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val workoutId: String,
    val timestampMillis: Long,
    val elapsedRealtimeMillis: Long,
    val movingElapsedMillis: Long,
    val segmentIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val provider: String?,
)

@Entity(
    tableName = "heart_rate_samples",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["workoutId", "elapsedRealtimeMillis"]), Index(value = ["workoutId", "timestampMillis"]), Index(value = ["workoutId"])],
)
data class HeartRateSampleEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val workoutId: String,
    val timestampMillis: Long,
    val elapsedRealtimeMillis: Long,
    val bpm: Int,
    val source: String,
)

data class WorkoutWithRoute(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val route: List<RoutePointEntity>,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val heartRateSamples: List<HeartRateSampleEntity>,
)

data class WorkoutAggregateRow(
    val workouts: Int,
    val distanceMeters: Double,
    val elapsedMillis: Long,
    val movingMillis: Long,
    val calories: Int,
)

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkout(workout: WorkoutEntity)

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoutePoint(point: RoutePointEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeartRateSample(sample: HeartRateSampleEntity): Long

    @Query("SELECT * FROM heart_rate_samples WHERE workoutId = :workoutId ORDER BY elapsedRealtimeMillis")
    suspend fun getHeartRateSamples(workoutId: String): List<HeartRateSampleEntity>

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getWorkout(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE sessionToken = :token LIMIT 1")
    suspend fun getWorkoutBySessionToken(token: String): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getWorkoutWithRoute(id: String): WorkoutWithRoute?

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    fun observeWorkoutWithRoute(id: String): Flow<WorkoutWithRoute?>

    @Query("SELECT * FROM workouts WHERE status IN ('ACTIVE','MANUAL_PAUSED','AUTO_PAUSED','PREPARING','FINISHING','RECOVERY_REQUIRED') ORDER BY startedAt DESC LIMIT 1")
    suspend fun getRecoverableWorkout(): WorkoutEntity?

    @Transaction
    @Query("SELECT * FROM workouts WHERE status IN ('ACTIVE','MANUAL_PAUSED','AUTO_PAUSED','PREPARING','FINISHING','RECOVERY_REQUIRED') ORDER BY startedAt DESC LIMIT 1")
    suspend fun getRecoverableWorkoutWithRoute(): WorkoutWithRoute?

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' AND type = :type ORDER BY startedAt DESC")
    fun observeHistoryByType(type: String): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' AND startedAt >= :fromInclusive AND startedAt < :toExclusive ORDER BY startedAt DESC")
    suspend fun getCompletedBetween(fromInclusive: Long, toExclusive: Long): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestWorkout(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestWorkout(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    suspend fun getAllCompleted(): List<WorkoutEntity>

    @Transaction
    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY startedAt DESC")
    suspend fun getAllCompletedWithRoutes(): List<WorkoutWithRoute>

    @Query("SELECT COUNT(*) AS workouts, COALESCE(SUM(distanceMeters),0.0) AS distanceMeters, COALESCE(SUM(elapsedMillis),0) AS elapsedMillis, COALESCE(SUM(movingMillis),0) AS movingMillis, COALESCE(SUM(caloriesEstimate),0) AS calories FROM workouts WHERE status = 'COMPLETED' AND startedAt >= :fromInclusive AND startedAt < :toExclusive")
    suspend fun aggregateBetween(fromInclusive: Long, toExclusive: Long): WorkoutAggregateRow

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: String): Int

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()

    @Transaction
    suspend fun deleteWorkoutTransactional(id: String): Boolean = deleteWorkout(id) == 1
}

@Database(
    entities = [WorkoutEntity::class, RoutePointEntity::class, HeartRateSampleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RunTrackDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var INSTANCE: RunTrackDatabase? = null

        fun get(context: Context): RunTrackDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                RunTrackDatabase::class.java,
                "runtrack.db",
            ).build().also { INSTANCE = it }
        }
    }
}
