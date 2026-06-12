package com.example.facedetectionapp.database



import androidx.room.*

// 1. Database Entity (The Table Schema)
@Entity(tableName = "registered_faces")
data class UserFaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_name") val name: String,
    @ColumnInfo(name = "face_embedding") val faceId: FloatArray // Stored securely via converter below
) {
    // Required overriding logic since entities holding arrays require custom equality rules
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserFaceEntity
        return id == other.id && name == other.name && faceId.contentEquals(other.faceId)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + faceId.contentHashCode()
        return result
    }
}

// 2. Data Access Object (The SQL Queries)
@Dao
interface UserFaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(userFace: UserFaceEntity)

    @Query("SELECT * FROM registered_faces")
    suspend fun getAllRegisteredFaces(): List<UserFaceEntity>
}

// 3. TypeConverter: Converts FloatArray to a String comma-separated list for SQL storage
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


