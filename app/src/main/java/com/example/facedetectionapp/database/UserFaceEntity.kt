package com.example.facedetectionapp.database

import androidx.room.*


@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"])]
)
data class FaceEmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val embeddingId: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "pose_type") val poseType: String,
    @ColumnInfo(name = "face_embedding") val faceId: FloatArray
) {

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


data class UserWithEmbeddings(
    @Embedded val user: UserEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "user_id"
    )
    val embeddings: List<FaceEmbeddingEntity>
)

@Dao
interface UserFaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: FaceEmbeddingEntity)


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

    @Transaction
    @Query("SELECT * FROM users")
    suspend fun getAllUsersWithEmbeddings(): List<UserWithEmbeddings>
}

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