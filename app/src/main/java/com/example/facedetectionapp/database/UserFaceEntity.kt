package com.example.facedetectionapp.database

import androidx.room.*

// ==========================================
// 1. DATABASE ENTITIES (The Table Schemas)
// ==========================================

/**
 * Represents the core profile data of a registered person.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

/**
 * Child table holding face embeddings captured at multiple angles.
 * Linked directly to UserEntity via an enforced cascading Foreign Key.
 */
@Entity(
    tableName = "user_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE // Wiping a user will automatically clear their vectors
        )
    ],
    indices = [Index(value = ["user_id"])]
)
data class FaceEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val embeddingId: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "pose_type") val poseType: String, // "CENTER", "LEFT", "RIGHT"
    @ColumnInfo(name = "face_embedding") val faceId: FloatArray
) {
    // Required overriding logic since entities holding primitive arrays require custom structural equality rules
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceEmbeddingEntity
        return embeddingId == other.embeddingId &&
                userId == other.userId &&
                poseType == other.poseType &&
                faceId.contentEquals(other.faceId)
    }

    override fun hashCode(): Int {
        var result = embeddingId
        result = 31 * result + userId
        result = 31 * result + poseType.hashCode()
        result = 31 * result + faceId.contentHashCode()
        return result
    }
}

// ==========================================
// 2. DATA RELATIONAL WRAPPER (For Matching)
// ==========================================

/**
 * Relational wrapper used to pull a user along with all 3 of their
 * saved biometric profiles simultaneously during verification phases.
 */
data class UserWithEmbeddings(
    @Embedded val user: UserEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "user_id"
    )
    val embeddings: List<FaceEmbeddingEntity>
)

// ==========================================
// 3. DATA ACCESS OBJECT (The SQL Queries)
// ==========================================

@Dao
interface UserFaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: FaceEmbeddingEntity)

    /**
     * Unified transaction wrapper. Creates the main user account profile
     * then sequentially bulk saves all 3 angle biometric vectors safely.
     */
    @Transaction
    suspend fun registerFullBiometricProfile(userName: String, embeddingsMap: Map<String, FloatArray>) {
        val assignedUserId = insertUser(UserEntity(name = userName)).toInt()

        embeddingsMap.forEach { (pose, vector) ->
            insertEmbedding(
                FaceEmbeddingEntity(
                    userId = assignedUserId,
                    poseType = pose,
                    faceId = vector
                )
            )
        }
    }

    /**
     * Fetches every single user and their corresponding multi-angle templates
     * to loop through during local facial validation comparisons.
     */
    @Transaction
    @Query("SELECT * FROM users")
    suspend fun getAllUsersWithEmbeddings(): List<UserWithEmbeddings>
}

// ==========================================
// 4. TYPE CONVERTERS (Data Serialization)
// ==========================================

class FaceIdConverter {
    @TypeConverter
    fun fromFloatArray(array: FloatArray): String {
        return array.joinToString(separator = ",")
    }

    @TypeConverter
    fun toFloatArray(data: String): FloatArray {
        if (data.isEmpty()) return FloatArray(0)
        return data.split(",").map { it.toFloat() }.toFloatArray()
    }
}