package redis

import (
	"github.com/go-redis/redis"
)

// 和点赞相关的接口
const (
	VotePromise = 1000
	Voteoppose = -1000
)
// PostVote 用户给某个讲座进行点赞
func PostVote(useid,postid string,dire float64)error{
	//查询用户之前点赞数据
	oldvalue := rdb.ZScore(getRedisKey(KeyPostVotedZsetPF+postid),useid).Val()
	pipeline := rdb.TxPipeline()
	if oldvalue == dire{
		//取消点赞，删除记录
		pipeline.ZRem(getRedisKey(KeyPostVotedZsetPF+postid),useid)
		//减去对应分数
		pipeline.ZIncrBy(getRedisKey(KeyPostScoreZset),Voteoppose,postid)
		pipeline.ZIncrBy(getRedisKey(KeyPostTimeZset),Voteoppose,postid)
	}else{
		pipeline.ZAdd(getRedisKey(KeyPostVotedZsetPF+postid),redis.Z{
			Score:  dire,
			Member: useid,
		})
		//增加分数
		pipeline.ZIncrBy(getRedisKey(KeyPostScoreZset),VotePromise,postid)
		pipeline.ZIncrBy(getRedisKey(KeyPostTimeZset),VotePromise,postid)
	}
	_,err := pipeline.Exec()
	return err
}

// GetPostVoted 获取点赞数量
func GetPostVoted(ids []string)(data []int64 , err error){
	pipeline := rdb.TxPipeline()
	for _,id := range ids{
		pipeline.ZCount(getRedisKey(KeyPostVotedZsetPF + id),"1","1")
	}
	cmders,err := pipeline.Exec()
	if err != nil {
		return nil,err
	}
	data = make([]int64,0,len(cmders))
	for _,cmder := range cmders{
		v := cmder.(*redis.IntCmd).Val()
		data = append(data,v)
	}
	return
}

// GetPostVotedByID 获取单个讲座点赞数量
func GetPostVotedByID(pid string)(num int64){
	return rdb.ZCount(getRedisKey(KeyPostVotedZsetPF + pid),"1","1").Val()
}

// GetPostStatues 获取点赞状态 （Exec遇到NIL 就报错，最后没有进行投票的人就会返回一个Redis：nil，暂时还没解决这个问题）
//func GetPostStatues(useid string,ids []string)(data []float64,err error){
//	pipeline := rdb.TxPipeline()
//	for _,id := range ids{
//		pipeline.ZScore(getRedisKey(KeyPostVotedZsetPF+id),useid)
//	}
//	cmders,err := pipeline.Exec()
//	if err != nil {
//		return nil,err
//	}
//	data = make([]float64,0,len(cmders))
//	for _,cmder := range cmders{
//		v := cmder.(*redis.FloatCmd).Val()
//		data = append(data,v)
//	}
//	return
//}
// GetPostStatues 获取单个帖子的点赞状态
func GetPostStatues(useid string,id string)(data float64){
	return rdb.ZScore(getRedisKey(KeyPostVotedZsetPF+id),useid).Val()
}