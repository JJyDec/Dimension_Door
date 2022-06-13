package redis

// 和redis相关的key

const (
	KeyPrefix = 					"dimension_door:"
	KeyPostTimeZset = 				"post:time" 		// 	zset帖子及发帖时间（综合排序）
	KeyPostScoreZset = 				"post:score" 		//	zset帖子及点赞分数（点赞数排序）
	KeyPostVotedZsetPF = 			"post:voted:" 		//	zset 记录用户及投票类型 参数是postid
	KeyPostTypeSetPF = 				"type:" 			// 	set保存分区类型下的id
	KeyMyLectureSetPF = 			"mylecture:"  		// 	set保存用户的讲座
	KeyLectureRemindZsetPF = 		"post:remind:" 		//	set讲座提醒 参数是 userid
	KeyLectureApplicationZsetPF = 	"post:apply:" 		//	set讲座报名 参数是 userid

)

// getRedisKey 给Redis Key 加前缀
func getRedisKey(key string)string{
	return KeyPrefix + key
}
